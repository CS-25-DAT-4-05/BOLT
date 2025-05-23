package SemanticAnalysis;

public class TypeError {
    private final String message;
    private final int lineNumber;
    private final String details;

    public TypeError(String message, int lineNumber, String details) {
        this.message = message;
        this.lineNumber = lineNumber;
        this.details = details;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (lineNumber > 0) {
            sb.append("Line ").append(lineNumber).append(": ");
        }

        sb.append("Error - ").append(message);

        if (details != null && !details.trim().isEmpty()) {
            sb.append(" (").append(details).append(")");
        }

        return sb.toString();
    }

    public String getMessage() { return message; }
    public int getLineNumber() { return lineNumber; }
    public String getDetails() { return details; }
}