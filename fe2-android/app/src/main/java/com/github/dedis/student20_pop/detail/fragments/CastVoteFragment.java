package com.github.dedis.student20_pop.detail.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.github.dedis.student20_pop.databinding.FragmentCastVoteBinding;
import com.github.dedis.student20_pop.detail.LaoDetailActivity;
import com.github.dedis.student20_pop.detail.LaoDetailViewModel;
import com.github.dedis.student20_pop.detail.adapters.QuestionViewPagerAdapter;
import com.github.dedis.student20_pop.model.Election;
import com.github.dedis.student20_pop.model.network.method.message.data.election.ElectionQuestion;
import com.github.dedis.student20_pop.model.network.method.message.data.election.ElectionVote;

import java.util.ArrayList;
import java.util.List;

import me.relex.circleindicator.CircleIndicator3;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link CastVoteFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class CastVoteFragment extends Fragment {

    private Button voteButton;
    private LaoDetailViewModel mLaoDetailViewModel;

    private View.OnClickListener buttonListener = v -> {
        voteButton.setEnabled(false);
        List<ElectionVote> electionVotes = new ArrayList<>();
        List<ElectionQuestion> electionQuestions = mLaoDetailViewModel.getCurrentElection().getElectionQuestions();
        for(int i = 0; i < electionQuestions.size(); i++){
            ElectionQuestion electionQuestion = electionQuestions.get(i);
            List<Integer> votes = mLaoDetailViewModel.getCurrentElectionVotes().get(i);
            ElectionVote electionVote = new ElectionVote(electionQuestion.getId(),votes,
                    electionQuestion.getWriteIn(),null,mLaoDetailViewModel.getCurrentElection().getId());
            electionVotes.add(electionVote);
        }
        mLaoDetailViewModel.sendVote(electionVotes);
    };



    public CastVoteFragment() {
        // Required empty public constructor
    }


    public static CastVoteFragment newInstance() {
        return new CastVoteFragment();
    }

    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        //Inflate the layout for this fragment
        FragmentCastVoteBinding mCastVoteFragBinding =
                FragmentCastVoteBinding.inflate(inflater, container, false);
         mLaoDetailViewModel = LaoDetailActivity.obtainViewModel(getActivity());

        TextView laoNameView = mCastVoteFragBinding.castVoteLaoName;
        TextView electionNameView = mCastVoteFragBinding.castVoteElectionName;

        //setUp the cast Vote button
        voteButton = mCastVoteFragBinding.castVoteButton;
        voteButton.setEnabled(false);

        //Getting election
        Election election = mLaoDetailViewModel.getCurrentElection();

        //Setting the Lao Name
        laoNameView.setText(mLaoDetailViewModel.getCurrentLaoName().getValue());

        //Setting election name
        electionNameView.setText(election.getName());

        int numberOfQuestions = election.getElectionQuestions().size();

        //Setting up the votes for the adapter
        mLaoDetailViewModel.setCurrentElectionVotes(setEmptyVoteList(numberOfQuestions));

        //Setting the viewPager and its adapter
        ViewPager2 viewPager2 = mCastVoteFragBinding.castVotePager;
        QuestionViewPagerAdapter adapter = new QuestionViewPagerAdapter(mLaoDetailViewModel, mCastVoteFragBinding);
        viewPager2.setAdapter(adapter);

        //Setting the indicator for horizontal swipe
        CircleIndicator3 circleIndicator = mCastVoteFragBinding.swipeIndicator;
        circleIndicator.setViewPager(viewPager2);


        voteButton.setOnClickListener(buttonListener);
        return mCastVoteFragBinding.getRoot();
    }

    private List<List<Integer>> setEmptyVoteList(int size){
        List<List<Integer>> votes = new ArrayList<>();
        for(int i = 0; i<size; i++){
            votes.add(new ArrayList<>());
        }
        return votes;
    }
}
