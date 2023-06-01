package com.freegang.fplugin.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.text.TextUtils
import android.util.Log
import com.freegang.fplugin.utils.findField
import com.freegang.fplugin.utils.findFieldAndGet
import com.freegang.fplugin.utils.findMethodAndInvoke
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Proxy


object PluginActivityBridge {
    const val TAG = "PluginBridge"
    const val ORIGINAL_INTENT_KEY = "original"
    const val PLUGIN_PROXY_ACTIVITY = "com.freegang.fplugin.activity.PluginProxyActivity"

    private var application: Application? = null

    /**
     * 方式一: 注册动态代理, 完成代理Activity替换
     *
     * @param application
     *
     */
    fun registerProxyActivity1(application: Application) {
        PluginActivityBridge.application = application
        hookAMS(application.packageName, PLUGIN_PROXY_ACTIVITY)
        hookATH()
        hookApm(application.packageName, PLUGIN_PROXY_ACTIVITY)
    }

    /**
     * 使用代理的Activity替换需要启动的未注册的Activity
     *
     * @param packageName 包名, 它应该是宿主App所在的顶层包名(AndroidManifest.xml中的package)
     * @param className 已经注册且可以被正常 startActivity 的 Activity (它就像是一个桥梁, 需要它瞒过 `Instrumentation#checkStartActivityResult` 的检查)
     *
     */
    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    fun hookAMS(packageName: String, className: String) {
        try {
            val singletonField: Field = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                //Android 8-, 获取到 gDefault 字段
                val clazz = Class.forName("android.app.ActivityManagerNative")
                clazz.findField("gDefault")
            } else {
                //Android 8+, 获取到 IActivityTaskManagerSingleton 字段
                val clazz = Class.forName("android.app.ActivityTaskManager")
                clazz.findField("IActivityTaskManagerSingleton")
            }

            val singleton = singletonField.get(null)

            // 获取到 mInstance 即是 IActivityTaskManager 的实例单例对象
            val singletonClazz = Class.forName("android.util.Singleton")
            val mInstanceField = singletonClazz.findField("mInstance")
            var mInstance = mInstanceField.get(singleton)

            //如果 mInstance 为空, 则主动调用一下 android.util.Singleton#get() 方法获取
            //这种情况通常发生在 Application#onCrate() 方法下
            if (mInstance == null) {
                mInstance = singletonClazz.findMethodAndInvoke(singleton!!, "get")
            }

            // 代理 IActivityTaskManager 类
            val atmClazz = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                //Android 9- 有一个灰名单限制
                Class.forName("android.app.IActivityManager")
            } else {
                Class.forName("android.app.IActivityTaskManager")
            }
            val mInstanceProxy = proxySingle(mInstance, atmClazz) { _, method, args ->
                if (method.name == "startActivity") {
                    args?.forEachIndexed { index, argIt ->
                        if (argIt is Intent) {
                            Log.d(TAG, "originalIntent: $argIt")
                            //替换
                            //将原始的activity保存下来, 为后续对消息达成欺骗
                            //替换原始intent为目标intent
                            if (!isRegistered(argIt)) {
                                val originalIntent = args[index] as Intent
                                val proxyIntent = Intent()
                                proxyIntent.component = ComponentName(packageName, className)
                                proxyIntent.putExtra(ORIGINAL_INTENT_KEY, originalIntent)
                                args[index] = proxyIntent
                            }

                        }
                    }
                }
            }

            // 替换
            mInstanceField.set(singleton, mInstanceProxy)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 对 ActivityThread 的 Handler-> mH 消息欺骗, 完成 intent 替换
     */
    @SuppressLint("PrivateApi")
    fun hookATH() {
        try {
            // 获取到 ActivityThread 中的静态字段 sCurrentActivityThread 即是它自身的实例对象
            val atClazz = Class.forName("android.app.ActivityThread")
            val sCurrentActivityThreadField = atClazz.findField("sCurrentActivityThread")
            val sCurrentActivityThread = sCurrentActivityThreadField.get(null)

            //获取到 mH 消息实现
            val mHField = atClazz.findField("mH")
            val mH = mHField.get(sCurrentActivityThread)

            //获取到Handler类中的 callback 字段
            val mCallbackField = Handler::class.java.findField("mCallback")

            //对 callback 进行重写
            mCallbackField.set(mH, object : Handler.Callback {
                override fun handleMessage(msg: Message): Boolean {
                    when (msg.what) {
                        100 -> { //Android 8- -> ActivityThread.H.EXECUTE_TRANSACTION = 100
                            val mIntentField = msg.obj::class.java.findField("intent")
                            val proxyIntent = mIntentField.get(msg.obj) as? Intent ?: return false
                            Log.d(TAG, "proxyIntent: $proxyIntent")
                            val originalIntent = proxyIntent.getParcelableExtra(ORIGINAL_INTENT_KEY) as? Intent ?: return false
                            mIntentField.set(msg.obj, originalIntent)  //替换回来, 对系统进行欺骗
                        }

                        159 -> { // Android 8+ -> ActivityThread.H.EXECUTE_TRANSACTION = 159
                            val ctClazz = Class.forName("android.app.servertransaction.ClientTransaction")
                            val mActivityCallbacksField = ctClazz.findField("mActivityCallbacks")
                            val mActivityCallbacks = mActivityCallbacksField.get(msg.obj) as List<*>

                            val launchActivityItemClazz = Class.forName("android.app.servertransaction.LaunchActivityItem")
                            for (launchActivityItem in mActivityCallbacks) {
                                if (launchActivityItemClazz.isInstance(launchActivityItem)) {
                                    val mIntentField = launchActivityItemClazz.findField("mIntent")
                                    val proxyIntent = mIntentField.get(launchActivityItem) as Intent
                                    Log.d(TAG, "proxyIntent: $proxyIntent")
                                    val originalIntent =
                                        proxyIntent.getParcelableExtra(ORIGINAL_INTENT_KEY) as? Intent ?: return false
                                    mIntentField.set(launchActivityItem, originalIntent)  //替换回来, 对系统进行欺骗
                                }
                            }
                        }
                    }
                    return false
                }
            })

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 未注册的Activity代理替换后虽然能够成功跳转, 但是会有一个[android.content.pm.PackageManager.NameNotFoundException]异常.
     * 在 Android 13 中会有一个[android.app.ApplicationPackageManager#getActivityInfo()]获取到ActivityInfo的操作(低版本不一样, 未作适配),
     * 这里需要再对其进行欺骗
     *
     * @param packageName 包名, 它应该是宿主App所在的顶层包名(AndroidManifest.xml中的package)
     * @param className 已经注册且可以被正常 startActivity 的 Activity (它就像是一个桥梁, 需要它瞒过 `ApplicationPackageManager#getActivityInfo` 的检查)
     *
     */
    @SuppressLint("PrivateApi")
    fun hookApm(packageName: String, className: String) {
        try {
            // 获取到 ActivityThread 中的静态字段 sPackageManager 一个静态的 IPackageManager
            val atClazz = Class.forName("android.app.ActivityThread")
            val sPackageManagerField = atClazz.findField("sPackageManager")
            val sPackageManager = sPackageManagerField.get(null)

            //动态代理 IPackageManager
            val ipm = Class.forName("android.content.pm.IPackageManager")
            val proxy = proxySingle(sPackageManager!!, ipm) { _, method, args ->
                if (method.name == "getActivityInfo") {
                    args?.forEachIndexed { index, _ ->
                        if (args[index] is ComponentName) {
                            //Log.d(TAG, "hookApm: args[$index] = $any")
                            args[index] = ComponentName(packageName, className)
                        }
                    }
                }
            }

            //替换
            sPackageManagerField.set(sPackageManager, proxy)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 方式二: 继承至 `Instrumentation` 重写 execStartActivity() 方法
     */
    @SuppressLint("PrivateApi")
    fun registerProxyActivity2(application: Application) {
        PluginActivityBridge.application = application

        try {
            // 获取到 ActivityThread 中的静态字段 sCurrentActivityThread 即是它自身的实例对象
            val atClazz = Class.forName("android.app.ActivityThread")
            val sCurrentActivityThreadFiled = atClazz.findField("sCurrentActivityThread")
            val sCurrentActivityThread = sCurrentActivityThreadFiled.get(null)

            val mInstrumentationFiled = atClazz.findField("mInstrumentation")
            val mInstrumentation = atClazz.findFieldAndGet<Instrumentation>(sCurrentActivityThread!!, "mInstrumentation")

            val instrumentationProxy = InstrumentationProxy(mInstrumentation)
            mInstrumentationFiled.set(sCurrentActivityThread, instrumentationProxy)

            //
            hookApm(application.packageName, PLUGIN_PROXY_ACTIVITY)
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    class InstrumentationProxy(private val instrumentation: Instrumentation) : Instrumentation() {

        fun execStartActivity(
            who: Context,
            contextThread: IBinder,
            token: IBinder,
            target: Activity,
            intent: Intent,
            requestCode: Int,
            options: Bundle?
        ): ActivityResult? {
            Log.d(TAG, "execStartActivity: originalIntent=$intent")
            if (!isRegistered(intent)) {
                val originalClassName = intent.component!!.className
                intent.putExtra(ORIGINAL_INTENT_KEY, originalClassName)
                intent.setClassName(who, PLUGIN_PROXY_ACTIVITY)
            }

            val execStartActivityMethod = Instrumentation::class.java.getDeclaredMethod(
                "execStartActivity",
                Context::class.java,
                IBinder::class.java,
                IBinder::class.java,
                Activity::class.java,
                Intent::class.java,
                Int::class.java,
                Bundle::class.java
            )
            return execStartActivityMethod.invoke(
                instrumentation,
                who,
                contextThread,
                token,
                target,
                intent,
                requestCode,
                options
            ) as ActivityResult?
        }

        override fun newActivity(cl: ClassLoader, className: String, intent: Intent): Activity {
            Log.d(TAG, "newActivity: proxyIntent=$intent")
            val originalClassName = intent.getStringExtra(ORIGINAL_INTENT_KEY)
            if (!TextUtils.isEmpty(originalClassName)) {
                return instrumentation.newActivity(cl, originalClassName, intent)
            }
            return instrumentation.newActivity(cl, className, intent)
        }
    }

    /**
     * Activity是否已经注册
     */
    private fun isRegistered(intent: Intent): Boolean {
        val queryIntentActivities = application!!.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        return queryIntentActivities.isNotEmpty()
    }

    /**
     * 单个类的代理处理
     */
    private fun proxySingle(instance: Any, clazz: Class<*>, block: (proxy: Any, method: Method, args: Array<Any>?) -> Unit): Any {
        if (!clazz.isInstance(instance)) {
            throw IllegalArgumentException("`Instance` should be the implementation class of `clazz`.")
        }

        return Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz)) { proxy, method, args ->
            block.invoke(proxy, method, args)
            //参数为空, 则调用 `invoke(mInstance` 否则, 调用 method.invoke(mInstance, *args)
            val result = if (args == null) method.invoke(instance) else method.invoke(instance, *args)
            //返回值为Void, 直接返回 Unit
            if (method.genericReturnType == Void.TYPE) Unit else result
        }
    }
}