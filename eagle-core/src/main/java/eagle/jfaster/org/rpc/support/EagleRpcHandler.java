package eagle.jfaster.org.rpc.support;

import eagle.jfaster.org.cluster.ReferCluster;
import eagle.jfaster.org.cluster.cluster.ReferClusterManage;
import eagle.jfaster.org.cluster.proxy.AbstractReferInvokeHandler;
import eagle.jfaster.org.cluster.proxy.AsyncInvokeHandler;
import eagle.jfaster.org.cluster.proxy.SyncInvokeHandler;
import eagle.jfaster.org.config.common.MergeConfig;
import eagle.jfaster.org.logging.InternalLogger;
import eagle.jfaster.org.logging.InternalLoggerFactory;
import eagle.jfaster.org.protocol.Protocol;
import eagle.jfaster.org.registry.factory.RegistryCenterManage;
import eagle.jfaster.org.rpc.Exporter;
import eagle.jfaster.org.rpc.RemoteInvoke;
import eagle.jfaster.org.rpc.RpcHandler;
import eagle.jfaster.org.spi.SpiClassLoader;
import eagle.jfaster.org.spi.SpiInfo;
import eagle.jfaster.org.util.RegistryUtil;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * Created by fangyanpeng1 on 2017/8/6.
 */
@SpiInfo(name = "eagle")
public class EagleRpcHandler implements RpcHandler {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(EagleRpcHandler.class);


    @Override
    public <T> ReferClusterManage<T> buildClusterManage(Class<T> interfaceClass, MergeConfig refConfig, List<MergeConfig> registryConfigs) {
        ReferClusterManage<T> clusterManage = new ReferClusterManage<T>(interfaceClass,refConfig,registryConfigs);
        clusterManage.init();
        return clusterManage;
    }

    @Override
    public <T> T refer(Class<T> interfaceClass, List<ReferCluster<T>> clusters) {
        AbstractReferInvokeHandler<T> invokeHandler;
        if(clusters.get(0).getConfig().getInvokeCallBack() == null){
            invokeHandler = new SyncInvokeHandler<T>(clusters,interfaceClass);
        }else {
            invokeHandler = new AsyncInvokeHandler<T>(clusters,interfaceClass);
        }
        return (T) Proxy.newProxyInstance(EagleRpcHandler.class.getClassLoader(),new Class[]{interfaceClass},invokeHandler);
    }

    @Override
    public <T> Exporter<T> export(Class<T> interfaceClass, T ref, MergeConfig serviceConfig, List<MergeConfig> registryConfigs) {
        String protoName = serviceConfig.getProtocol();
        Protocol<T> protocol = SpiClassLoader.getClassLoader(Protocol.class).getExtension(protoName);
        RemoteInvoke<T> invoke = new EagleRpcRemoteInvoke<T>(interfaceClass,ref,serviceConfig);
        Exporter<T> exporter = protocol.createServer(invoke);
        RegistryCenterManage registryManage;
        for(MergeConfig regConfig : registryConfigs){
            registryManage = SpiClassLoader.getClassLoader(RegistryCenterManage.class).getExtension(regConfig.getProtocol());
            registryManage.registerService(regConfig,serviceConfig);
        }
        return exporter;
    }

    @Override
    public <T> void unexport(List<Exporter<T>> exporters, List<MergeConfig> registryConfigs) {
        try {
            RegistryUtil.closeRegistrys(registryConfigs);
            for(Exporter exporter : exporters){
                exporter.close();
            }
        } catch (IOException e) {
            logger.error("EagleRpcHandler.unexport exception",e);
        }
    }

    @Override
    public <T> void unRef(List<ReferClusterManage<T>> clusterManages) {
        for (ReferClusterManage manage : clusterManages){
            manage.destroy();
        }
    }
}
