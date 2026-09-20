package hx.wardriver.hayos.background;

public interface ProgressListener {
    void onRequestProgress(long bytesWritten, long contentLength);
}