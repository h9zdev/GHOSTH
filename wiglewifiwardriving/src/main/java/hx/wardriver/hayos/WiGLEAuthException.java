package hx.wardriver.hayos;

/**
 * Created by arkasha on 1/13/17.
 * Authentication Exception when accessing protected resources
 */

public class WiGLEAuthException extends Exception {

    public WiGLEAuthException(String message) {
        super(message);
    }
}
