import React, { useState } from 'react';
import {
  TouchableOpacity, View, ViewStyle,
} from 'react-native';
import { Spacing } from 'styles';
import TextBlock from 'components/TextBlock';
import styleEventView from 'styles/stylesheets/eventView';
import ListCollapsibleIcon from 'components/eventList/ListCollapsibleIcon';
import { useSelector } from 'react-redux';
import ParagraphBlock from 'components/ParagraphBlock';
import { Lao } from 'model/objects';
import { makeCurrentLao } from 'store/reducers';
import EdiText from 'react-editext';
import PropTypes from 'prop-types';

function renderProperties(lao: Lao, isOrganizer : boolean) {
  const style = {
    fontFamily: 'Helvetica Bold',
    fontSize: '13px',
    width: 200,
  };
  return (
    <>
      <ParagraphBlock text="Lao name: " />
      <EdiText
        hint="type the new LAO name"
        viewProps={{ style: style }}
        inputProps={{ style: style }}
        type="text"
        onSave={
          () => {
          // TODO: carry out the necessary LAO update interactions with the backend here
        }
        }
        value={`${lao.name}`}
        
      />
      <ParagraphBlock text="Lao creation: " />
      <EdiText
        hint="type the new creation date"
        viewProps={{ style: style }}
        inputProps={{ style: style }}
        type="text"
        onSave={
          () => {
            console.log("Value of IsOrganizer = " + isOrganizer)
          // TODO: carry out the necessary LAO update interactions with the backend here
        }
        }
        value={`${lao.creation.toString()}`}
      />
    </>
  );
}
const propTypes = {
  isOrganizer : Boolean
}
type IPropTypes = PropTypes.InferProps<typeof propTypes>;

const LaoProperties = (props : IPropTypes) => {
  const laoSelect = makeCurrentLao();
  const lao = useSelector(laoSelect);
  const {isOrganizer} = props;
  const [toggleChildrenVisible, setToggleChildrenVisible] = useState(false);

  const toggleChildren = () => (setToggleChildrenVisible(!toggleChildrenVisible));

  return (
    <>
      <TextBlock bold text="Lao Properties" />
      <View style={[styleEventView.default, { marginTop: Spacing.s }]}>
        <TouchableOpacity onPress={toggleChildren} style={{ textAlign: 'right' } as ViewStyle}>
          <ListCollapsibleIcon isOpen={toggleChildrenVisible} />
        </TouchableOpacity>

        { toggleChildrenVisible && lao && renderProperties(lao, isOrganizer) }
      </View>
    </>
  );
};

export default LaoProperties;
