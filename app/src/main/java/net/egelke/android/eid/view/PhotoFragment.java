package net.egelke.android.eid.view;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import net.egelke.android.eid.EidSuiteApp;
import net.egelke.android.eid.R;
import net.egelke.android.eid.viewmodel.Photo;
import net.egelke.android.eid.viewmodel.UpdateListener;

public class PhotoFragment extends Fragment implements UpdateListener<Photo> {

    private static final String TAG = "net.egelke.android.eid";

    private Photo p;

    private ProgressBar loading;

    private ImageView image;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_photo, container, false);

        loading = (ProgressBar) rootView.findViewById(R.id.loading_photo);
        image = (ImageView) rootView.findViewById(R.id.photo);

        p = ((EidSuiteApp) getActivity().getApplication()).getViewObject(Photo.class);
        onUpdate(p);
        p.addListener(this);

        return rootView;
    }

    @Override
    public void onDestroyView() {
        p.removeListener(this);
        super.onDestroyView();
    }

    @Override
    public void onUpdate(Photo p) {
        if (image == null || loading == null) return;

        if (this.p != p)
            Log.w(TAG, "Updated object isn't the the instance object");

        if (p.isUpdating()) {
            image.setVisibility(View.GONE);
            loading.setVisibility(View.VISIBLE);
        } else {
            if (p.getDrawable() == null)
                image.setImageResource(android.R.color.transparent);
            else
                image.setImageDrawable(p.getDrawable());

            loading.setVisibility(View.GONE);
            image.setVisibility(View.VISIBLE);
        }
    }
}


