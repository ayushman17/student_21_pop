import React from 'react';

import PROPS_TYPE from 'res/Props';
import ParagraphBlock from 'components/ParagraphBlock';
import TimeDisplay from 'components/TimeDisplay';
import PropTypes from 'prop-types';

/**
 * Component used to display a Meeting event in the LAO event list
 */

const EventMeeting = (props: IPropTypes) => {
  const { event } = props;

  return (
    <>
      <TimeDisplay start={event.start.valueOf()} />
      { event.end && <TimeDisplay end={event.end.valueOf()} /> }
      { event.location && <ParagraphBlock text={event.location} /> }
    </>
  );
};

const propTypes = {
  event: PROPS_TYPE.meeting.isRequired,
};
EventMeeting.propTypes = propTypes;

type IPropTypes = PropTypes.InferProps<typeof propTypes>;

export default EventMeeting;
