package play.modules.liquibase;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.exception.LiquibaseException;
import liquibase.exception.ValidationFailedException;
import liquibase.lockservice.LockService;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.CompositeResourceAccessor;
import liquibase.resource.FileSystemResourceAccessor;
import liquibase.resource.ResourceAccessor;

import org.hibernate.JDBCException;

import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.db.DB;
import play.utils.Properties;

public class LiquibasePlugin extends PlayPlugin {

	@Override
	public void onApplicationStart() {
		
		String autoupdate = Play.configuration.getProperty("liquibase.active");
		String mainchangelogpath = Play.configuration.getProperty("liquibase.changelog", "mainchangelog.xml");
		String propertiespath = Play.configuration.getProperty("liquibase.properties", "liquibase.properties");
		String contexts = Play.configuration.getProperty("liquibase.contexts");
		String actions = Play.configuration.getProperty("liquibase.actions");
		
		if (null == actions) {
			throw new LiquibaseUpdateException("No valid action found for liquibase operation");
		}
		
		List<LiquibaseAction> acts = new ArrayList<LiquibaseAction>();
		
		for (String action : actions.split(",")) {
			LiquibaseAction op = LiquibaseAction.valueOf(action.toUpperCase());
			acts.add(op);
		}
		
		Database db = null;
		
		if (null != autoupdate && "true".equals(autoupdate)) {
			Logger.info("Auto update flag found and positive => let's get on with changelog update");
			try {
				
				/**
				String url = Play.configuration.getProperty("db.url");
				String username = Play.configuration.getProperty("db.user");
				String password = Play.configuration.getProperty("db.pass");
				String driver = Play.configuration.getProperty("db.driver");
				
				Database database = CommandLineUtils.createDatabaseObject(Play.classloader, url, username, password, driver, null, null);
				*/
				Connection cnx = DB.datasource.getConnection();
				ResourceAccessor r = new CompositeResourceAccessor(new ClassLoaderResourceAccessor(Play.classloader), new FileSystemResourceAccessor(Play.applicationPath.getAbsolutePath()));
				
				Liquibase liquibase = new Liquibase(mainchangelogpath, new ClassLoaderResourceAccessor(), new JdbcConnection(cnx));
				InputStream stream = Play.classloader.getResourceAsStream(propertiespath);

				if (null != stream) {
					Properties props = new Properties();
					props.load(stream);
					
					for (String key:props.keySet()) {
						String val = props.get(key);
						Logger.info("found parameter [%1$s] / [%2$s] for liquibase update", key, val);
						liquibase.setChangeLogParameter(key, val);
					}
				} else {
					Logger.warn("Could not find properties file [%s]", propertiespath);
				}
				
				db = liquibase.getDatabase();
				for (LiquibaseAction op: acts) {
					Logger.info("Dealing with op [%s]", op);
					
					switch (op) {
						case LISTLOCKS:
							liquibase.reportLocks(System.out);
							break;
						case RELEASELOCKS :
							LockService.getInstance(db).forceReleaseLock();
							break;
						case SYNC :
							liquibase.changeLogSync(contexts);					
							break;
						case STATUS:
							File tmp = Play.tmpDir.createTempFile("liquibase", ".status");
							liquibase.reportStatus(true, contexts, new FileWriter(tmp));
							Logger.info("status dumped into file [%s]", tmp);
							break;
						case UPDATE:
							liquibase.update(contexts);
							break;
						case CLEARCHECKSUMS:
							liquibase.clearCheckSums();
							break;
						case VALIDATE:
							try {
			                    liquibase.validate();
			                } catch (ValidationFailedException e) {
			                    Logger.error(e,"liquibase validation");
			                }
						default:
							break;
					}
					Logger.info("op [%s] performed",op);
				}
			} catch (SQLException sqe) { 
				throw new LiquibaseUpdateException(sqe.getMessage());				
			} catch (LiquibaseException e) { 
				throw new LiquibaseUpdateException(e.getMessage());
			} catch (IOException ioe) {
				throw new LiquibaseUpdateException(ioe.getMessage());				
			} finally {
				if (null != db) {
					try {						
						db.close();
					} catch (DatabaseException e) {
						Logger.warn(e,"problem closing connection");
					} catch (JDBCException jdbce) {
						Logger.warn(jdbce,"problem closing connection");
					}
				}
			}

		} else {
			Logger.info("Auto update flag set to false or not available => skipping structural update");
		}
	}
}
