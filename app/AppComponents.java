import javax.sql.DataSource;

import play.ApplicationLoader;
import play.BuiltInComponentsFromContext;
import play.components.AkkaComponents;
import play.components.ConfigurationComponents;
import play.data.FormFactoryComponents;
import play.filters.components.HttpFiltersComponents;
import play.libs.concurrent.HttpExecutionContext;
import play.routing.Router;
import router.Routes;

import controllers.UploadController;

public class AppComponents extends BuiltInComponentsFromContext
        implements HttpFiltersComponents, ConfigurationComponents, AkkaComponents, FormFactoryComponents {

    public AppComponents(ApplicationLoader.Context context) {
        super(context);
    }

    @Override
    public Router router() {
        HttpExecutionContext httpExecutionContext = new HttpExecutionContext(actorSystem().dispatcher());

        UploadController uploadController = new UploadController(config(), actorSystem(), httpExecutionContext);

        return new Routes(scalaHttpErrorHandler(), uploadController).asJava();
    }
}
