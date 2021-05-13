import play.Application;
import play.ApplicationLoader;
import play.LoggerConfigurator;

public class AppLoader implements ApplicationLoader {
    @Override
    public Application load(Context context) {
        LoggerConfigurator.apply(context.environment().classLoader())
                .ifPresent(loggerConfigurator -> loggerConfigurator.configure(context.environment(),
                        context.initialConfig()));

        return new AppComponents(context).application();
    }
}
