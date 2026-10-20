package de.remotemc.plugin.api;

/**
 * Error returned to the app as {"error": code, "message": text}. The app translates the
 * stable code; the message is an English fallback for other clients (curl, scripts).
 */
public final class ApiException extends Exception {

    final int status;
    final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(400, code, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(403, "forbidden", message);
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(404, code, message);
    }
}
