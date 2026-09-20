package hx.wardriver.hayos.net;

public interface AuthenticatedRequestCompletedListener<T, JSONObject> extends RequestCompletedListener<T, JSONObject>{
    void onAuthenticationRequired();
}
