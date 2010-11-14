package play.modules.liquibase;

import play.exceptions.PlayException;

public class LiquibaseException extends PlayException {

	public String getErrorTitle() {
		return "Liquibase Error";
	}

	@Override
	public String getErrorDescription() {
        return String.format("A DBUpdate error occured (%s): <strong>%s</strong>", getMessage(), getCause() == null ? "" : getCause().getMessage());
	}
	
    public LiquibaseException(String message) {
        super(message, null);
    }

    public LiquibaseException(String message, Throwable cause) {
        super(message, cause);
    }

}
