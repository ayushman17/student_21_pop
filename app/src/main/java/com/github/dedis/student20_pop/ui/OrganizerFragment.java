package com.github.dedis.student20_pop.ui;


import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseExpandableListAdapter;
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
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import com.github.dedis.student20_pop.PoPApplication;
import com.github.dedis.student20_pop.R;
import com.github.dedis.student20_pop.model.Event;
import com.github.dedis.student20_pop.model.Lao;
import com.github.dedis.student20_pop.utility.ui.OnAddWitnessListener;
import com.github.dedis.student20_pop.utility.ui.OnEventTypeSelectedListener;
import com.github.dedis.student20_pop.utility.ui.OnEventTypeSelectedListener.EventType;
import com.github.dedis.student20_pop.utility.ui.WitnessListAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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


    /**
     * Enum class for each event category
     */
    private enum EventCategory {
        PAST, PRESENT, FUTURE
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            onEventTypeSelectedListener = (OnEventTypeSelectedListener) context;
            onAddWitnessListener = (OnAddWitnessListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement listeners");
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
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
        listViewEventAdapter = new OrganizerExpandableListViewEventAdapter(this.getActivity(), events);
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

    public class OrganizerExpandableListViewEventAdapter extends BaseExpandableListAdapter {
        private final Context context;
        private final List<EventCategory> categories;
        private final HashMap<EventCategory, List<Event>> eventsMap;

        /**
         * Constructor for the expandable list view adapter to display the events
         * in the organizer UI
         *
         * @param context
         * @param events  the list of events of the lao
         */
        public OrganizerExpandableListViewEventAdapter(Context context, List<Event> events) {
            this.context = context;
            this.eventsMap = new HashMap<>();
            this.categories = new ArrayList<>();
            this.categories.add(EventCategory.PAST);
            this.categories.add(EventCategory.PRESENT);
            this.categories.add(EventCategory.FUTURE);
            this.eventsMap.put(EventCategory.PAST, new ArrayList<>());
            this.eventsMap.put(EventCategory.PRESENT, new ArrayList<>());
            this.eventsMap.put(EventCategory.FUTURE, new ArrayList<>());

            putEventsInMap(events, this.eventsMap);
            orderEventsInMap(this.eventsMap);

        }

        /**
         * @return the amount of categories
         */
        @Override
        public int getGroupCount() {
            return this.eventsMap.size();
        }

        /**
         * @param groupPosition
         * @return the amount of events in a given group
         */
        @Override
        public int getChildrenCount(int groupPosition) {
            return this.eventsMap.get(this.categories.get(groupPosition)).size();
        }

        /**
         * @param groupPosition
         * @return the category of a given position
         */
        @Override
        public Object getGroup(int groupPosition) {
            if (groupPosition >= getGroupCount()) {
                return null;
            }
            return this.categories.get(groupPosition);
        }

        /**
         * @param groupPosition
         * @param childPosition
         * @return the event for a given position in a given category
         */
        @Override
        public Object getChild(int groupPosition, int childPosition) {

            if (groupPosition >= getGroupCount()) {
                return null;
            }
            if (childPosition >= getChildrenCount(groupPosition)) {
                return null;
            }

            return this.eventsMap.get(this.categories.get(groupPosition)).get(childPosition);
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        /**
         * @param groupPosition
         * @param isExpanded
         * @param convertView
         * @param parent
         * @return the view for a given category
         */
        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
            String eventCategory = "";
            switch ((EventCategory) getGroup(groupPosition)) {
                case PAST:
                    eventCategory = getString(R.string.past_events);
                    break;
                case PRESENT:
                    eventCategory = getString(R.string.present_events);
                    break;
                case FUTURE:
                    eventCategory = getString(R.string.future_events);
                    break;
            }

            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.layout_event_category, null);
            }

            TextView eventTextView = convertView.findViewById(R.id.event_category);
            eventTextView.setText(eventCategory);

            ImageButton addEvent = convertView.findViewById(R.id.add_future_event_button);
            addEvent.setVisibility((getGroup(groupPosition) == EventCategory.FUTURE) ? View.VISIBLE : View.GONE);
            addEvent.setFocusable(View.NOT_FOCUSABLE);
            addEvent.setOnClickListener(v -> {
                AlertDialog.Builder builderSingle = new AlertDialog.Builder(context);
                builderSingle.setTitle(R.string.select_event_type_dialog_title);

                final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(context, android.R.layout.select_dialog_singlechoice);

                arrayAdapter.insert(context.getString(R.string.meeting_event), EventType.MEETING.ordinal());
                arrayAdapter.insert(context.getString(R.string.roll_call_event), EventType.ROLL_CALL.ordinal());
                arrayAdapter.insert(context.getString(R.string.poll_event), EventType.POLL.ordinal());

                builderSingle.setNegativeButton(context.getString(R.string.button_cancel), (dialog, which) -> dialog.dismiss());
                builderSingle.setAdapter(arrayAdapter, (dialog, which) -> {
                    onEventTypeSelectedListener.OnEventTypeSelectedListener(EventType.values()[which]);
                });
                builderSingle.show();
            });


            return convertView;
        }

        /**
         * @param groupPosition
         * @param childPosition
         * @param isLastChild
         * @param convertView
         * @param parent
         * @return the view for a given event
         */
        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {

            Event event = ((Event) getChild(groupPosition, childPosition));
            String eventTitle = (event.getName() + " : " + event.getType());

            //For now, later: for each specific type of event, show the required content
            String eventDescription = "Time : " + event.getTime() + "\nLocation : " + event.getLocation();


            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                //now:
                convertView = inflater.inflate(R.layout.layout_event, null);
                //later:
            /*
            switch (event.getType()) {
                case MEETING:
                    convertView = inflater.inflate(R.layout.meeting_layout, null);
                    break;
                case ROLL_CALL:
                    convertView = inflater.inflate(R.layout.rollcall_layout, null);
                    break;
                case POLL:
                    convertView = inflater.inflate(R.layout.poll_layout, null);
                    break;
                case DISCUSSION:
                    convertView = inflater.inflate(R.layout.discussion_layout, null);
                    break;
                default:
                    convertView = inflater.inflate(R.layout.event_layout, null);
                    break;
            }
             */
            }

            TextView eventTitleTextView = convertView.findViewById(R.id.event_title);
            eventTitleTextView.setText(eventTitle);
            //later: put this in the switch, depending on what else to display
            TextView descriptionTextView = convertView.findViewById(R.id.event_description);
            descriptionTextView.setText(eventDescription);
            return convertView;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }

        /**
         * A helper method that places the events in the correct key-value pair
         * according to their times
         *
         * @param events
         * @param eventsMap
         */
        private void putEventsInMap(List<Event> events, HashMap<EventCategory, List<Event>> eventsMap) {
            //TODO: make the difference clear between PAST and PRESENT
            //For now, the event are put in the different categories according to their time attribute
            //Later, according to the start/end-time
            for (Event event : events) {
                //for now (testing purposes)
                //later: event.getEndTime() < now
                if (event.getTime() < (System.currentTimeMillis() / 1000L)) {
                    eventsMap.get(EventCategory.PAST).add(event);
                }
                //later: event.getStartTime()<now && event.getEndTime() > now
                else if (event.getTime() <= (System.currentTimeMillis() / 1000L)) {
                    eventsMap.get(EventCategory.PRESENT).add(event);
                } else { //if e.getStartTime() > now
                    eventsMap.get(EventCategory.FUTURE).add(event);
                }
            }
        }

        /**
         * A helper method that orders the events according to their times
         *
         * @param eventsMap
         */
        private void orderEventsInMap(HashMap<EventCategory, List<Event>> eventsMap) {

            for (EventCategory category : categories) {
                Collections.sort(eventsMap.get(category), new EventComparator());
            }
            //2 possibilities: B strictly after A or B nested within A
        }

        private class EventComparator implements Comparator<Event> {
            //later: compare start times
            @Override
            public int compare(Event event1, Event event2) {
                return Long.compare(event1.getTime(), event2.getTime());
            }
        }
    }
}
