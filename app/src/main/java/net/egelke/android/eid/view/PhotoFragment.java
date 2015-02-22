package net.egelke.android.eid.view;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import net.egelke.android.eid.R;
import net.egelke.android.eid.viewmodel.UpdateListener;
import net.egelke.android.eid.viewmodel.ViewModel;

public class PhotoFragment extends Fragment implements UpdateListener {

    private ProgressBar loading;

    private ImageView image;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_photo, container, false);

        loading = (ProgressBar) rootView.findViewById(R.id.loading_photo);
        image = (ImageView) rootView.findViewById(R.id.photo);

        image.setImageDrawable((Drawable) ViewModel.getData("Photo"));
        ViewModel.addListener(this);

        return rootView;
    }

    @Override
    public void onDestroyView() {
        ViewModel.removeListener(this);
        super.onDestroyView();
    }

    @Override
    public void startUpdate(String key) {
        if ("Photo".equals(key)) {
            image.setVisibility(View.GONE);
            loading.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void updateFinished(String key, Object oldValue, Object newValue) {
        if ("Photo".equals(key)) {
            image.setImageDrawable((Drawable) newValue);

            loading.setVisibility(View.GONE);
            image.setVisibility(View.VISIBLE);
        }
    }
}


