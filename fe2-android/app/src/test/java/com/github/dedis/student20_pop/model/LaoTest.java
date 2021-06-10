package com.github.dedis.student20_pop.model;

import org.junit.Test;
import java.lang.Object;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

public class LaoTest {

    private static final String LAO_1_ID = "lao1Id";
    private static final String LAO_NAME_1 = "LAO name 1";
    private static final String ORGANIZER = "0x2365";
    private static final String rollCallId1 = "rollCallId1";
    private static final String rollCallId2 = "rollCallId2";
    private static final String rollCallId3 = "rollCallId3";
    private static final String electionId1 = "electionId1";
    private static final String electionId2 = "rollCallId2";
    private static final String electionId3 = "rollCallId3";
    private static final Set<String> WITNESSES = new HashSet<>(Arrays.asList("0x3434", "0x4747"));
    private static final Set<String> WITNESSES_WITH_NULL = new HashSet<>(Arrays.asList("0x3939", null, "0x4747"));

    private static final Lao LAO_1 = new Lao(LAO_1_ID, LAO_NAME_1);
    private static final Map<String, RollCall> rollCalls = new HashMap<String, RollCall>() {{
        put(rollCallId1, new RollCall());
        put(rollCallId2, new RollCall());
        put(rollCallId3, new RollCall());
    }};
    private static final Map<String, Election> elections = new HashMap<String, Election>() {{
        put(electionId1, new Election());
        put(electionId2, new Election());
        put(electionId3, new Election());
    }};

    @Test
    public void removeRollCallTest() {
        LAO_1.setRollCalls(new HashMap<>(rollCalls));
        assert (LAO_1.removeRollCall(rollCallId3)); // we want to assert that we can remove rollCallId3 successfully
        assert (LAO_1.getRollCalls().size() == 2);
        assert (LAO_1.getRollCalls().containsKey(rollCallId1));
        assert (LAO_1.getRollCalls().containsKey(rollCallId2));
        assert (!LAO_1.getRollCalls().containsKey(rollCallId3));

        LAO_1.setRollCalls(new HashMap<String, RollCall>() {{
                               put(rollCallId1, new RollCall());
                               put(null, new RollCall());
                               put(rollCallId3, new RollCall());
                           }}
        );
        assert (!LAO_1.removeRollCall(rollCallId2));
    }

    @Test
    public void removeElectionTest() {
        LAO_1.setElections(new HashMap<>(elections));
        assert (LAO_1.removeElection(electionId3)); // we want to assert that we can remove electionId3 successfully
        assert (LAO_1.getElections().size() == 2);
        assert (LAO_1.getElections().containsKey(electionId1));
        assert (LAO_1.getElections().containsKey(electionId2));
        assert (!LAO_1.getElections().containsKey(electionId3));

        // we remove electionId2
        LAO_1.setElections(new HashMap<String, Election>() {{
                               put(electionId1, new Election());
                               put(null, new Election());
                               put(electionId3, new Election());
                           }}
        );
        // now the removal of electionId2 can't be done
        assert (!LAO_1.removeElection(electionId2));
    }

    @Test
    public void updateRollCalls() {

        LAO_1.setRollCalls(new HashMap<>(rollCalls));
        RollCall r1 = new RollCall();
        r1.setId("New r1 id");
        LAO_1.updateRollCall(rollCallId1, r1);
        assert (!LAO_1.getRollCalls().containsKey(rollCallId1));
        assert (LAO_1.getRollCalls().containsKey("New r1 id"));
        assert (LAO_1.getRollCalls().containsKey(rollCallId2));
        assert (LAO_1.getRollCalls().containsKey(rollCallId3));
        assert (LAO_1.getRollCalls().get("New r1 id") == r1);

        // we create a different roll call that has the same Id as the first one
        RollCall r2 = new RollCall();
        r2.setId(r1.getId());

        LAO_1.updateRollCall(r1.getId(), r2);
        assert (LAO_1.getRollCalls().get(r1.getId()) != r1);
        assert (LAO_1.getRollCalls().get(r1.getId()) == r2);


    }

    @Test
    public void updateRollCallWithNull() {
        assertThrows(IllegalArgumentException.class, () -> LAO_1.updateRollCall("random", null));
    }

    @Test
    public void updateElections() {

        LAO_1.setElections(new HashMap<>(elections));
        Election e1 = new Election();
        e1.setId("New e1 id");
        LAO_1.updateElection(electionId1, e1);
        assert (!LAO_1.getElections().containsKey(electionId1));
        assert (LAO_1.getElections().containsKey("New e1 id"));
        assert (LAO_1.getElections().containsKey(electionId2));
        assert (LAO_1.getElections().containsKey(electionId3));
        assert (LAO_1.getElections().get("New e1 id") == e1);

        // we create a different election that has the same Id as the first one
        Election e2 = new Election();
        e2.setId(e1.getId());

        LAO_1.updateElection(e1.getId(), e2);
        assert (LAO_1.getElections().get(e1.getId()) != e1);
        assert (LAO_1.getElections().get(e1.getId()) == e2);

    }

    @Test
    public void updateElectionCallWithNull() {
        assertThrows(IllegalArgumentException.class, () -> LAO_1.updateElection("random", null));
    }


    @Test
    public void createLaoNullParametersTest() {
        assertThrows(IllegalArgumentException.class, () -> new Lao(null, LAO_NAME_1));
        assertThrows(IllegalArgumentException.class, () -> new Lao(LAO_1_ID, null));
        assertThrows(IllegalArgumentException.class, () -> new Lao(null));
    }

    @Test
    public void createLaoEmptyNameTest() {
        assertThrows(IllegalArgumentException.class, () -> new Lao(LAO_1_ID, ""));
    }

    @Test
    public void createLaoEmptyIdTest() {
        assertThrows(IllegalArgumentException.class, () -> new Lao(""));
    }

    @Test
    public void setAndGetNameTest() {
        assertThat(LAO_1.getName(), is(LAO_NAME_1));
        LAO_1.setName("New Name");
        assertThat(LAO_1.getName(), is("New Name"));
    }

    @Test
    public void setAndGetOrganizerTest() {
        LAO_1.setOrganizer(ORGANIZER);
        assertThat(LAO_1.getOrganizer(), is(ORGANIZER));
    }

    @Test
    public void setAndGetRollCalls() {
        LAO_1.setRollCalls(rollCalls);
        assertThat(LAO_1.getRollCalls(), is(rollCalls));
    }

    @Test
    public void getRollCall() {
        RollCall r1 = new RollCall();
        RollCall r2 = new RollCall();
        LAO_1.setRollCalls(new HashMap<String, RollCall>() {{
            put(rollCallId1, r1);
            put(rollCallId2, r2);
        }});
        assertThat(LAO_1.getRollCall(rollCallId1).get(), is(r1));
    }

    @Test
    public void setAndGetElections() {
        LAO_1.setElections(elections);
        assertThat(LAO_1.getElections(), is(elections));
    }

    @Test
    public void getElection() {
        Election e1 = new Election();
        Election e2 = new Election();
        LAO_1.setElections(new HashMap<String, Election>() {{
            put(electionId1, e1);
            put(electionId2, e2);
        }});
        assertThat(LAO_1.getElection(electionId1).get(), is(e1));
    }

    @Test
    public void setAndGetWitnessesTest() {

        LAO_1.setWitnesses(WITNESSES);
        assertThat(LAO_1.getWitnesses(), is(WITNESSES));
    }


    @Test
    public void setNullNameTest() {
        assertThrows(IllegalArgumentException.class, () -> LAO_1.setName(null));
    }

    @Test
    public void setEmptyNameTest() {
        assertThrows(IllegalArgumentException.class, () -> LAO_1.setName(""));
    }

    @Test
    public void setNullWitnessesTest() {
        assertThrows(IllegalArgumentException.class, () -> LAO_1.setWitnesses(null));
        assertThrows(IllegalArgumentException.class, () -> LAO_1.setWitnesses(WITNESSES_WITH_NULL));
    }

    @Test
    public void getAndSetChannelTest() {
       LAO_1.setChannel("new channel");
       assertThat(LAO_1.getChannel(),is("new channel"));
    }

    @Test
    public void setAndGetModificationIdTest() {
        LAO_1.setModificationId("Modification Id");
        assertThat(LAO_1.getModificationId(),is("Modification Id"));
    }

    @Test
    public void setAndGetCreation() {
        LAO_1.setCreation(new Long(0xFF));
        assertThat(LAO_1.getCreation(),is(new Long(0xFF)));
    }

    @Test
    public void setAndGetLastModified() {
        LAO_1.setLastModified(new Long(0xFF));
        assertThat(LAO_1.getLastModified(),is(new Long(0xFF)));
    }

    @Test
    public void setAndGetId() {
        LAO_1.setId("New Id");
        assertThat(LAO_1.getId(),is("New Id"));
    }

}
