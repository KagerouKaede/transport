package com.tsAdmin.common;

import com.jfinal.config.*;
import com.jfinal.plugin.druid.DruidPlugin;
import com.jfinal.render.ViewType;
import com.jfinal.template.Engine;
import com.jfinal.plugin.activerecord.ActiveRecordPlugin;
import com.jfinal.kit.PropKit;
import com.tsAdmin.control.SandboxController;
import com.tsAdmin.control.DataController;
import com.tsAdmin.control.IndexController;

public class MainConfig extends JFinalConfig
{
    /** 配置常量 */
    @Override
    public void configConstant(Constants me)
    {
        PropKit.use("sql.properties");
        // me.setDevMode(false);
        // me.setError404View("/common/404.html");
        // me.setError500View("/common/500.html");
        me.setViewType(ViewType.FREE_MARKER);
    }

    /** 配置路由 */
    @Override
    public void configRoute(Routes me)
    {
        me.add("/", IndexController.class);
        me.add("/data", DataController.class);
        me.add("/sandbox", SandboxController.class);
    }

    /** 配置插件 */
    @Override
    public void configPlugin(Plugins me)
    {
        // 配置 Druid 数据库连接池插件
        DruidPlugin druidPlugin = new DruidPlugin(
            PropKit.get("jdbcUrl"),
            PropKit.get("user"),
            PropKit.get("password")
        );

        me.add(druidPlugin);

        // 配置 ActiveRecord 插件
        ActiveRecordPlugin arp = new ActiveRecordPlugin(druidPlugin);
        me.add(arp);
    }

    /** 配置全局拦截器 */
    @Override
    public void configInterceptor(Interceptors me) {}

    /** 配置处理器 */
    @Override
    public void configHandler(Handlers me) {}

    @Override
    public void configEngine(Engine arg0) {}
}
