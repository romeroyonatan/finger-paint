package yuku.ambilwarna;

public interface OnAmbilWarnaListener {
    void onCancel(AmbilWarnaDialog dialog);
    void onOk(AmbilWarnaDialog dialog, int color);
}
