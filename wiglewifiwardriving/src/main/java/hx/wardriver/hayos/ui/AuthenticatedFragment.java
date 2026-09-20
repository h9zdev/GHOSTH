package hx.wardriver.hayos.ui;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import hx.wardriver.hayos.R;

public class AuthenticatedFragment extends Fragment {
    public void showAuthDialog() {
        final FragmentActivity fa = getActivity();
        if (null != fa) {
            WiGLEAuthDialog.createDialog(fa, getString(R.string.login_title),
                    getString(R.string.login_required), getString(R.string.login),
                    getString(R.string.cancel));
        }
    }
}
