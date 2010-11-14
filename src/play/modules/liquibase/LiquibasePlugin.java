package play.modules.liquibase;

import java.io.IOException;
import java.sql.Connection;

import liquibase.ClassLoaderFileOpener;
import liquibase.Liquibase;
import liquibase.exception.LiquibaseException;
import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.db.DB;
import play.db.jpa.JPAPlugin;
import play.utils.Properties;

public class LiquibasePlugin extends PlayPlugin {

	@Override
	public void onApplicationStart() {
		
		String autoupdate = Play.configuration.getProperty("liquibase.active");
		String mainchangelogpath = Play.configuration.getProperty("liquibase.changelog", "mainchangelog.xml");
		String propertiespath = Play.configuration.getProperty("liquibase.properties", "liquibase.properties");
		String contexts = Play.configuration.getProperty("liquibase.contexts");
		
		if (null != autoupdate && "true".equals(autoupdate)) {
			Logger.info("Auto update flag found and positive => let's get on with changelog update");
			try {
				
				JPAPlugin.startTx(false);
				
				Connection cnx = DB.getConnection();
				Liquibase liquibase = new Liquibase(mainchangelogpath, new ClassLoaderFileOpener(), cnx);
				Properties props = new Properties();
				props.load(Play.classloader.getResourceAsStream(propertiespath ));
				
				for (String key:props.keySet()) {
					Logger.info("found key parameter [%s] for liquibase update", key);
					liquibase.setChangeLogParameterValue(key, props.get(key));
				}
				
				Logger.info("Ready for database diff generation");
				liquibase.validate();
				Logger.info("Validation Ok");
				liquibase.changeLogSync(contexts);
				Logger.info("Changelog Execution performed");
				JPAPlugin.closeTx(false);
			
			} catch (LiquibaseException e) {
				JPAPlugin.closeTx(true);
				throw new LiquibaseException(e.getMessage());
			} catch (IOException ioe) {
				JPAPlugin.closeTx(true);
				throw new LiquibaseException(ioe.getMessage());				
			}
		} else {
			Logger.info("Auto update flag set to false or not available => skipping structural update");
		}
	}
}
