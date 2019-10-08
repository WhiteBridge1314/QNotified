package nil.nadph.qnotified;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import nil.nadph.qnotified.pk.FriendChunk;
import nil.nadph.qnotified.record.ConfigManager;
import nil.nadph.qnotified.ui.DebugDrawable;
import nil.nadph.qnotified.util.*;

import java.io.File;
import java.io.FileInputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.HashSet;

import static android.widget.LinearLayout.LayoutParams.MATCH_PARENT;
import static android.widget.LinearLayout.LayoutParams.WRAP_CONTENT;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static nil.nadph.qnotified.ActProxyMgr.*;
import static nil.nadph.qnotified.util.Initiator.load;
import static nil.nadph.qnotified.util.Utils.*;
import nil.nadph.qnotified.ipc.*;

/*TitleKit:Lcom/tencent/mobileqq/widget/navbar/NavBarCommon*/

public class QQMainHook implements IXposedHookLoadPackage {
	public static WeakReference<Activity> splashActivityRef;
    public static final String QN_FULL_TAG = "qn_full_tag";
    XC_LoadPackage.LoadPackageParam lpparam;
	
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam _lpparam) throws Throwable {
        try {
            this.lpparam = _lpparam;
            XC_MethodHook startup = new XC_MethodHook(51) {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        Utils.checkLogFlag();
                        Context ctx = null;
                        Class clz = param.thisObject.getClass().getClassLoader().loadClass("com.tencent.common.app.BaseApplicationImpl");
                        Field f = hasField(clz, "sApplication");
                        if (f == null) ctx = (Context) sget_object(clz, "a", clz);
                        else ctx = (Context) f.get(null);
                        ClassLoader classLoader = ctx.getClassLoader();
                        Initiator.init(classLoader);
                        if (classLoader == null) throw new AssertionError("ERROR:classLoader==null");
                        injectStartupHook(ctx);
                        Class director = load("com/tencent/mobileqq/startup/director/StartupDirector");
                        if (director == null)
                            director = load("com/tencent/mobileqq/startup/director/StartupDirector$1").getDeclaredField("this$0").getType();
                        Class loadData = load("com/tencent/mobileqq/startup/step/LoadData");
                        Method doStep = null;
                        for (Method method : loadData.getDeclaredMethods()) {
                            if (method.getReturnType().equals(boolean.class) && method.getParameterTypes().length == 0) {
                                doStep = method;
                                break;
                            }
                        }
                        final Class __director = director;
                        XposedBridge.hookMethod(doStep, new XC_MethodHook(51) {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                Object dir = iget_object_or_null(param.thisObject, "mDirector", __director);
                                if (dir == null) dir = iget_object_or_null(param.thisObject, "a", __director);
                                InjectDelayableHooks.step(dir);
                            }
                        });
                    } catch (Throwable e) {
                        log(e);
                        throw e;
                    }
                }
            };
            Class loadDex = lpparam.classLoader.loadClass("com.tencent.mobileqq.startup.step.LoadDex");
            Method[] ms = loadDex.getDeclaredMethods();
            Method m = null;
            for (Method method : ms) {
                if (method.getReturnType().equals(boolean.class) && method.getParameterTypes().length == 0) {
                    m = method;
                    break;
                }
            }
            XposedBridge.hookMethod(m, startup);
            //findAndHookMethodIfExists("com.tencent.common.app.QFixApplicationImpl", lpparam.classLoader, "isAndroidNPatchEnable", XC_MethodReplacement.returnConstant(500, false));
        } catch (Throwable e) {
            if ((e + "").contains("com.bug.zqq")) return;
            log(e);
            throw e;
        }
    }

    private void injectStartupHook(Context ctx) {
        if (Utils.DEBUG) {
            if ("true".equals(System.getProperty(QN_FULL_TAG))) {
                log("Err:QNotified reloaded??");
                //return;
                System.exit(-1);
                //QNotified updated(in HookLoader mode),kill QQ to make user restart it.
            }
            System.setProperty(QN_FULL_TAG, "true");
        }

        Class clazz = load(ActProxyMgr.STUB_ACTIVITY);
        if (clazz != null) {
            ActProxyMgr mgr = ActProxyMgr.getInstance();
            findAndHookMethod(clazz, "onCreate", Bundle.class, mgr);
            findAndHookMethodIfExists(clazz, "doOnDestroy", mgr);
            findAndHookMethodIfExists(clazz, "onActivityResult", int.class, int.class, Intent.class, mgr);
            findAndHookMethodIfExists(clazz, "doOnPause", mgr);
            findAndHookMethodIfExists(clazz, "doOnResume", mgr);
            findAndHookMethodIfExists(clazz, "isWrapContent", mgr);
        }

		/*
		 try {
		 Method m = null;
		 Method[] methods = load("com.tencent.mobileqq.activity.BaseChatPie").getDeclaredMethods();
		 for (int i = 0; i < methods.length; i++) {
		 Method method = methods[i];
		 if (method.getName().equals("e") && method.getParameterTypes().length == 0 && method.getReturnType() == Void.TYPE) {
		 m = methods[i];
		 break;
		 }
		 }
		 assert m != null;
		 XposedBridge.hookMethod(m, new XC_MethodHook(51) {
		 @Override
		 public void afterHookedMethod(MethodHookParam methodHookParam) throws Throwable {
		 if (ConfigManager.get().getBooleanOrFalse(qn_send_card_msg)) {
		 final Object qqi = iget_object(methodHookParam.thisObject, "a", load("com.tencent.mobileqq.app.QQAppInterface"));
		 final Object session = iget_object(methodHookParam.thisObject, "a", load("com.tencent.mobileqq.activity.aio.SessionInfo"));
		 final ViewGroup viewGroup = (ViewGroup) iget_object(methodHookParam.thisObject, "d", Class.forName("android.view.ViewGroup"));
		 Resources res = viewGroup.getContext().getResources();
		 int id_btn = res.getIdentifier("fun_btn", "id", null);
		 final int id_et = res.getIdentifier("input", "id", null);
		 if (viewGroup != null)
		 ((Button) viewGroup.findViewById(id_btn).setOnLongClickListener(new View.OnLongClickListener() {
		 @Override
		 public boolean onLongClick(View view) {
		 EditText edit = (EditText) viewGroup.findViewById(id_et);
		 String input = edit.getText().toString();
		 boolean success = false;
		 Class cl_msgMgr = load((String) Hook.config.get("MessageManager"));
		 try {
		 Object msg = invoke_static(load((String) Hook.config.get("TestStructMsg")), "a", input, load("com.tencent.mobileqq.structmsg.AbsStructMsg"));
		 if (msg != null) {
		 invoke_static(cl_msgMgr, "a", qqi, session, msg);
		 success = true;
		 }
		 } catch (Throwable th) {
		 Toast.makeText(view.getContext(), th.toString(), Toast.LENGTH_SHORT).show();
		 XposedBridge.log(th);
		 }
		 try {
		 Object arkMsg = new_instance(load("com.tencent.mobileqq.data.ArkAppMessage"));
		 if ((Boolean) invoke_virtual(arkMsg, "fromAppXml", input)) {
		 invoke_static(cl_msgMgr, "a", qqi, session, arkMsg);
		 success = true;
		 }
		 } catch (Throwable th2) {
		 XposedBridge.log(th2);
		 }
		 if (success) edit.setText("");
		 return false;
		 }
		 }));

		 }
		 }
		 });
		 } catch (Throwable e) {
		 log(e);
		 }

		 /*
		 findAndHookMethod(load("friendlist/DelFriendReq"),"writeTo",load("com/qq/taf/jce/JceOutputStream"),new XC_MethodHook(70){
		 @Override
		 protected void beforeHookedMethod(MethodHookParam param) throws Throwable{
		 Field f=param.thisObject.getClass().getDeclaredField("delType");
		 f.setAccessible(true);
		 f.set(param.thisObject,(byte)2);
		 }
		 });

		 //findAndHookMethod(load("friendlist/AddFriendReq"),"writeTo",load("com/qq/taf/jce/JceOutputStream"),invokeRecord);
		 /*findAndHookMethod(load("friendlist/AddFriendReq"),"writeTo",load("com/qq/taf/jce/JceOutputStream"),new XC_MethodHook(10){
		 @Override
		 protected void beforeHookedMethod(MethodHookParam param) throws Throwable{
		 Field f=param.thisObject.getClass().getDeclaredField("sourceSubID");
		 f.setAccessible(true);
		 f.set(param.thisObject,1);
		 f=param.thisObject.getClass().getDeclaredField("sourceID");
		 f.setAccessible(true);
		 f.set(param.thisObject,3071);
		 f=param.thisObject.getClass().getDeclaredField("myfriendgroupid");
		 f.setAccessible(true);
		 f.set(param.thisObject,(byte)0);
		 /*f=param.thisObject.getClass().getDeclaredField("adduinsetting");
		 f.setAccessible(true);
		 f.set(param.thisObject,4);*

		 }
		 });//*/
        asyncStartFindClass();
        hideMiniAppEntry();
		SyncUtils.initBroadcast(ctx);
    }

    
 
    private void hideMiniAppEntry() {
        try {
            if (Utils.isTim(getApplication())) return;
        } catch (Exception ignored) {
        }
        try {
            ConfigManager cfg = ConfigManager.getDefault();
            if (cfg.getBooleanOrFalse(qn_hide_msg_list_miniapp)) {
                int lastVersion = cfg.getIntOrDefault("qn_hide_msg_list_miniapp_version_code", 0);
                if (getHostInfo(getApplication()).versionCode == lastVersion) {
                    String methodName = cfg.getString("qn_hide_msg_list_miniapp_method_name");
                    findAndHookMethod(load("com/tencent/mobileqq/activity/Conversation"), methodName, XC_MethodReplacement.returnConstant(null));
                } else {
                    Class con = load("com/tencent/mobileqq/activity/Conversation");
                    for (Method m : con.getDeclaredMethods()) {
                        Class[] ps = m.getParameterTypes();
                        if (ps != null && ps.length > 0) continue;
                        if (!m.getReturnType().equals(void.class)) continue;
                        String name = m.getName();
                        if (!Modifier.isPrivate(m.getModifiers())) continue;
                        if (name.length() > 1) continue;
                        char c = name.charAt(0);
                        if ('F' <= c && c < 'a')
                            XposedBridge.hookMethod(m, new XC_MethodReplacement(30) {
                                @Override
                                protected Object replaceHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                                    try {
                                        Method m = (Method) param.method;
                                        m.setAccessible(true);
                                        XposedBridge.invokeOriginalMethod(m, param.thisObject, param.args);
                                    } catch (InvocationTargetException e) {
                                        if (!(e.getCause() instanceof UnsupportedOperationException)) {
                                            log(e);
                                        }
                                    } catch (Throwable t) {
                                        log(t);
                                    }
                                    return null;
                                }
                            });
                    }
					/*try {
					 findAndHookMethod(load("com.tencent.mobileqq.app.FrameFragment"), "createTabContent", String.class, new XC_MethodReplacement(39) {
					 @Override
					 protected Object replaceHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
					 try {
					 Method m = (Method) param.method;
					 m.setAccessible(true);
					 XposedBridge.invokeOriginalMethod(m, param.thisObject, param.args);
					 } catch (UnsupportedOperationException e) {
					 } catch (Throwable t) {
					 log(t);
					 }
					 return null;
					 }
					 });
					 } catch (Exception e) {}*/
                    Class miniapp = load("com/tencent/mobileqq/mini/entry/MiniAppEntryAdapter");
                    if (miniapp == null)
                        miniapp = load("com/tencent/mobileqq/mini/entry/MiniAppEntryAdapter$1").getDeclaredField("this$0").getType();
                    XposedBridge.hookAllConstructors(miniapp, new XC_MethodHook(60) {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String methodName = null;
                            StackTraceElement[] stacks = new Throwable().getStackTrace();
                            for (int i = 0; i < stacks.length; i++) {
                                if (stacks[i].getClassName().indexOf("Conversation") != -1) {
                                    methodName = stacks[i].getMethodName();
                                    break;
                                }
                            }
                            if (methodName == null)
                                throw new NullPointerException("Failed to get Conversation.?() to hide MiniApp!");
                            ConfigManager cfg = ConfigManager.getDefault();
                            cfg.putString("qn_hide_msg_list_miniapp_method_name", methodName);
                            cfg.getAllConfig().put("qn_hide_msg_list_miniapp_version_code", getHostInfo(getApplication()).versionCode);
                            cfg.save();
                            param.setThrowable(new UnsupportedOperationException("MiniAppEntry disabled"));
                        }
                    });
                }
            }
        } catch (Exception e) {
            log(e);
        }
    }

    

    private void asyncStartFindClass() {
        if (DexKit.tryLoadOrNull(DexKit.C_DIALOG_UTIL) == null)
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ignored) {
                    }
                    DexKit.doFindClass(DexKit.C_DIALOG_UTIL);
                }
            }).start();
        if (DexKit.tryLoadOrNull(DexKit.C_FACADE) == null)
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException ignored) {
                    }
                    DexKit.doFindClass(DexKit.C_FACADE);
                }
            }).start();
        if (DexKit.tryLoadOrNull(DexKit.C_FLASH_PIC_HELPER) == null)
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(8000);
                    } catch (InterruptedException ignored) {
                    }
                    DexKit.doFindClass(DexKit.C_FLASH_PIC_HELPER);
                }
            }).start();
    }

    

    public XC_MethodHook invokeRecord = new XC_MethodHook(200) {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws IllegalAccessException, IllegalArgumentException {
            Member m = param.method;
            String ret = m.getDeclaringClass().getSimpleName() + "->" + ((m instanceof Method) ? m.getName() : "<init>") + "(";
            Class[] argt;
            if (m instanceof Method)
                argt = ((Method) m).getParameterTypes();
            else if (m instanceof Constructor)
                argt = ((Constructor) m).getParameterTypes();
            else argt = new Class[0];
            for (int i = 0; i < argt.length; i++) {
                if (i != 0) ret += ",\n";
                ret += param.args[i];
            }
            ret += ")=" + param.getResult();
            Utils.log(ret);
            ret = "↑dump object:" + m.getDeclaringClass().getCanonicalName() + "\n";
            Field[] fs = m.getDeclaringClass().getDeclaredFields();
            for (int i = 0; i < fs.length; i++) {
                fs[i].setAccessible(true);
                ret += (i < fs.length - 1 ? "├" : "↓") + fs[i].getName() + "=" + ClazzExplorer.en_toStr(fs[i].get(param.thisObject)) + "\n";
            }
            log(ret);
            Utils.dumpTrace();
        }
    };

    public static XC_MethodHook.Unhook findAndHookMethodIfExists(Class<?> clazz, String methodName, Object...
            parameterTypesAndCallback) {
        try {
            return findAndHookMethod(clazz, methodName, parameterTypesAndCallback);
        } catch (Throwable e) {
            log(e.toString());
            return null;
        }
    }

    public static XC_MethodHook.Unhook findAndHookMethodIfExists(String clazzName, ClassLoader cl, String
            methodName, Object... parameterTypesAndCallback) {
        try {
            return findAndHookMethod(clazzName, cl, methodName, parameterTypesAndCallback);
        } catch (Throwable e) {
            log(e.toString());
            return null;
        }
    }

    public static void startProxyActivity(Context ctx, int action) {
        Intent intent = new Intent(ctx, load(ActProxyMgr.STUB_ACTIVITY));
        int id = ActProxyMgr.next();
        intent.putExtra(ACTIVITY_PROXY_ID_TAG, id);
        intent.putExtra(ACTIVITY_PROXY_ACTION, action);
        intent.putExtra("fling_action_key", 2);
        intent.putExtra("fling_code_key", ctx.hashCode());
        ctx.startActivity(intent);
    }

    public static void openProfileCard(Context ctx, long uin) {
        try {
            Parcelable allInOne = (Parcelable) new_instance(load("com/tencent/mobileqq/activity/ProfileActivity$AllInOne"), "" + uin, 35, String.class, int.class);
            Intent intent = new Intent(ctx, load("com/tencent/mobileqq/activity/FriendProfileCardActivity"));
            intent.putExtra("AllInOne", allInOne);
            ctx.startActivity(intent);
        } catch (Exception e) {
            log(e);
        }
    }

    

}
