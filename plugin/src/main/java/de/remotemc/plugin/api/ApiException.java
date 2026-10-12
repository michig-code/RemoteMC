package de.remotemc.plugin.api;

/**
 * Error returned to the app as {"error": code, "message": text}. The app translates the
 * stable code; the message is an English fallback for other clients (curl, scripts).
 */
final class ApiException extends Exception {

    final int status;
    final String code;

    ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    static ApiException badRequest(String code, String message) {
        return new ApiException(400, code, message);
    }

    static ApiException forbidden(String message) {
        return new ApiException(403, "forbidden", message);
    }

    static ApiException notFound(String code, String message) {
        return new ApiException(404, code, message);
    }
}
