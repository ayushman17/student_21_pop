package com.github.dedis.student20_pop.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.dedis.student20_pop.PoPApplication;
import com.github.dedis.student20_pop.R;
import com.github.dedis.student20_pop.model.Event;
import com.github.dedis.student20_pop.model.Lao;
import com.github.dedis.student20_pop.utility.ui.WitnessListAdapter;
import com.github.dedis.student20_pop.utility.ui.organizer.OnAddWitnessListener;
import com.github.dedis.student20_pop.utility.ui.organizer.OnEventCreatedListener;
import com.github.dedis.student20_pop.utility.ui.organizer.OnEventTypeSelectedListener;
import com.github.dedis.student20_pop.utility.ui.EventAdapter.OrganizerExpandableListViewEventAdapter;

import java.util.ArrayList;
import java.util.List;

public class OrganizerFragment extends Fragment {

    public static final String TAG = AttendeeFragment.class.getSimpleName();
    private OrganizerExpandableListViewEventAdapter listViewEventAdapter;
    private ExpandableListView expandableListView;
    private Lao lao;
    private Button propertiesButton;
    private ImageButton editPropertiesButton;
    private ImageButton addWitnessButton;
    private Button confirmButton;
    private OnEventTypeSelectedListener onEventTypeSelectedListener;
    private OnAddWitnessListener onAddWitnessListener;
    private EditText laoNameEditText;
    private TextView laoNameTextView;
    private ListView witnessesListView;
    private ListView witnessesEditListView;


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnEventCreatedListener)
            onEventTypeSelectedListener = (OnEventTypeSelectedListener) context;
        else
            throw new ClassCastException(context.toString() + " must implement OnEventTypeSelectedListener");

        if (context instanceof OnAddWitnessListener)
            onAddWitnessListener = (OnAddWitnessListener) context;
        else
            throw new ClassCastException(context.toString() + " must implement OnAddWitnessListener");

    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        PoPApplication app = (PoPApplication) (getActivity().getApplication());

        lao = app.getCurrentLao();
        List<Event> events = app.getEvents(lao);
        //Display Properties
        View rootView = inflater.inflate(R.layout.fragment_organizer, container, false);

        //Layout Properties fields
        ViewSwitcher viewSwitcher = rootView.findViewById(R.id.viewSwitcher);
        View propertiesView = rootView.findViewById(R.id.properties_view);
        laoNameTextView = propertiesView.findViewById(R.id.organization_name);
        laoNameTextView.setText(lao.getName());

        final WitnessListAdapter adapter = new WitnessListAdapter(getActivity(), (ArrayList<String>) app.getWitnesses(lao));
        witnessesListView = propertiesView.findViewById(R.id.witness_list);
        witnessesListView.setAdapter(adapter);

        editPropertiesButton = rootView.findViewById(R.id.edit_button);
        editPropertiesButton
                .setVisibility(
                        ((viewSwitcher.getNextView().getId() == R.id.properties_edit_view) &&
                                (viewSwitcher.getVisibility() == View.VISIBLE)) ?
                                View.VISIBLE : View.GONE);

        //Layout Edit Properties fields
        View propertiesEditView = rootView.findViewById(R.id.properties_edit_view);
        laoNameEditText = propertiesEditView.findViewById(R.id.organization_name_editText);
        laoNameEditText.setText(lao.getName());
        witnessesEditListView = propertiesEditView.findViewById(R.id.witness_edit_list);
        witnessesEditListView.setAdapter(adapter);

        addWitnessButton = propertiesEditView.findViewById(R.id.add_witness_button);
        confirmButton = propertiesEditView.findViewById(R.id.properties_edit_confirm);

        propertiesButton = rootView.findViewById(R.id.tab_properties);
        propertiesButton.setOnClickListener(
                clicked -> {
                    viewSwitcher.setVisibility((viewSwitcher.getVisibility() == View.GONE) ? View.VISIBLE : View.GONE);
                    editPropertiesButton
                            .setVisibility(
                                    ((viewSwitcher.getNextView().getId() == R.id.properties_edit_view) &&
                                            (viewSwitcher.getVisibility() == View.VISIBLE)) ?
                                            View.VISIBLE : View.GONE);
                });


        //Display Events
        expandableListView = rootView.findViewById(R.id.organizer_expandable_list_view);
        listViewEventAdapter = new OrganizerExpandableListViewEventAdapter(this.getActivity(), events, onEventTypeSelectedListener);
        expandableListView.setAdapter(listViewEventAdapter);
        expandableListView.expandGroup(0);
        expandableListView.expandGroup(1);

        propertiesEditView.findViewById(R.id.properties_edit_cancel)
                .setOnClickListener(c -> {
                    viewSwitcher.showNext();
                    editPropertiesButton.setVisibility(View.VISIBLE);
                    addWitnessButton.setVisibility(View.GONE);
                });

        editPropertiesButton.setOnClickListener(
                clicked -> {
                    viewSwitcher.showNext();
                    editPropertiesButton.setVisibility(View.GONE);
                    addWitnessButton.setVisibility(View.VISIBLE);
                }
        );

        addWitnessButton.setOnClickListener(
                clicked -> {
                    onAddWitnessListener.onAddWitnessListener();
                }
        );

        confirmButton.setOnClickListener(
                clicked -> {
                    String title = laoNameEditText.getText().toString().trim();
                    if (!title.isEmpty()) {
                        lao = lao.setName(title);
                        viewSwitcher.showNext();
                        laoNameTextView.setText(laoNameEditText.getText());
                        editPropertiesButton.setVisibility(View.VISIBLE);
                        addWitnessButton.setVisibility(View.GONE);
                        // TODO : If LAO's name has changed : tell backend to update it
                    } else {
                        Toast.makeText(getContext(), getString(R.string.exception_message_empty_lao_name), Toast.LENGTH_SHORT).show();
                    }
                }
        );

        return rootView;
    }
}