package com.github.dedis.student20_pop.detail.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.github.dedis.student20_pop.databinding.FragmentElectionResultBinding;
import com.github.dedis.student20_pop.detail.LaoDetailActivity;
import com.github.dedis.student20_pop.detail.LaoDetailViewModel;
import com.github.dedis.student20_pop.detail.adapters.ElectionResultPagerAdapter;
import com.github.dedis.student20_pop.model.Election;

import java.util.Arrays;

import me.relex.circleindicator.CircleIndicator3;

public class ElectionResultFragment extends Fragment {

    private TextView laoNameView;
    private TextView electionNameView;
    private FragmentElectionResultBinding mElectionResultFragBinding;
    private LaoDetailViewModel mLaoDetailViewModel;

    public ElectionResultFragment() {
        // Required empty public constructor
    }


    public static ElectionResultFragment newInstance() {
        return new ElectionResultFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        //Inflate the layout for this fragment
        mElectionResultFragBinding =
                FragmentElectionResultBinding.inflate(inflater, container, false);
        mLaoDetailViewModel = LaoDetailActivity.obtainViewModel(getActivity());

        laoNameView = mElectionResultFragBinding.electionResultLaoName;
        electionNameView = mElectionResultFragBinding.electionResultPresentationTitle;

        //Getting election
        Election election = mLaoDetailViewModel.getCurrentElection();

        ///// Setting up static data for testing //////////////////////////////////////////////////////
        if(election == null)
            election = new Election();

        election.setName("Election 1");
        election.setQuestions(Arrays.asList("Who for 1st delegate", "Who for 2nd delegate"));
        election.setBallotsOptions(Arrays.asList(Arrays.asList("A", "B"), Arrays.asList("C", "D", "E")));
        election.setVotes(Arrays.asList(Arrays.asList(21, 6), Arrays.asList(2,7,9)));
        /////////////////////////////////////////////////////////////////////////////////////////////////


        //Setting the Lao Name
        laoNameView.setText(mLaoDetailViewModel.getCurrentLaoName().getValue());
       // laoNameView.setText("Some Title");
        //Setting election name
          electionNameView.setText(election.getName());

        ElectionResultPagerAdapter adapter = new ElectionResultPagerAdapter(mLaoDetailViewModel);
        ViewPager2 viewPager2 = mElectionResultFragBinding.electionResultPager;
        viewPager2.setAdapter(adapter);

        //Setting the circle indicator
        CircleIndicator3 circleIndicator = mElectionResultFragBinding.swipeIndicatorElectionResults;
        circleIndicator.setViewPager(viewPager2);

        mElectionResultFragBinding.setLifecycleOwner(getActivity());
        return mElectionResultFragBinding.getRoot();
    }
}
