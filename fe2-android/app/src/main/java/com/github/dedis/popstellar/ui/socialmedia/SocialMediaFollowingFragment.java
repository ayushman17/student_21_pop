package com.github.dedis.popstellar.ui.socialmedia;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.dedis.popstellar.databinding.SocialMediaFollowingFragmentBinding;

public class SocialMediaFollowingFragment extends Fragment {

  private static final String TAG = SocialMediaFollowingFragment.class.getSimpleName();

  private SocialMediaFollowingFragmentBinding mSocialMediaFollowingFragBinding;
  private SocialMediaViewModel mSocialMediaViewModel;

  public static SocialMediaFollowingFragment newInstance() {
    return new SocialMediaFollowingFragment();
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    mSocialMediaFollowingFragBinding =
        SocialMediaFollowingFragmentBinding.inflate(inflater, container, false);

    mSocialMediaViewModel = SocialMediaActivity.obtainViewModel(getActivity());

    mSocialMediaFollowingFragBinding.setViewModel(mSocialMediaViewModel);
    mSocialMediaFollowingFragBinding.setLifecycleOwner(getActivity());

    return mSocialMediaFollowingFragBinding.getRoot();
  }
}