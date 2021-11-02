import React from 'react';
import renderer from 'react-test-renderer';
import Enzyme, { shallow } from 'enzyme';
import Adapter from '@wojtekmaj/enzyme-adapter-react-17';
import TextInputChirp from '../TextInputChirp';

Enzyme.configure({ adapter: new Adapter() });

/*
const BIG_MESSAGE = 'It seems that this message won\'t fit. It seems that this message won\'t fit. '
  + 'It seems that this message won\'t fit. It seems that this message won\'t fit. It seems that '
  + 'this message won\'t fit. It seems that this message won\'t fit. It seems that this message '
  + 'won\'t fit. It seems that this message won\'t fit.';
*/

describe('TextInputChirp', () => {
  it('renders correctly without placeholder', () => {
    const tree = renderer.create(
      <TextInputChirp onChangeText={() => {}} onPress={() => {}} />,
    ).toJSON();
    expect(tree).toMatchSnapshot();
  });

  it('renders correctly with placeholder', () => {
    const placeholder = 'Placeholder';
    const tree = renderer.create(
      <TextInputChirp onChangeText={() => {}} onPress={() => {}} placeholder={placeholder} />,
    ).toJSON();
    expect(tree).toMatchSnapshot();
  });

  it('calls onChange correctly', () => {
    const helloWorld = 'Hello world !';
    const onChangeText = jest.fn();
    const onPress = jest.fn();
    const wrapper = shallow(<TextInputChirp onChangeText={onChangeText} onPress={onPress} />);
    wrapper.find('TextInput').simulate('changeText', helloWorld);
    expect(onChangeText).toHaveBeenCalledWith(helloWorld);
  });

  /*
  it('calls onPress correctly', () => {
    const onChangeText = jest.fn();
    const onPress = jest.fn();
    const wrapper = shallow(<TextInputChirp onChangeText={onChangeText} onPress={onPress} />);
    wrapper.find('Button').simulate('click');
    expect(onPress).toHaveBeenCalled();
  });

  it('counts chars correctly', () => {
    const helloWorld = 'Hello world !';
    const remainingChars = 300 - helloWorld.length;
    const onChangeText = jest.fn();
    const onPress = jest.fn();
    const wrapper = shallow(<TextInputChirp onChangeText={onChangeText} onPress={onPress} />);
    wrapper.find('TextInput').simulate('changeText', helloWorld);
    const remainingCharsText = wrapper.find('TextBlock');
    expect(remainingCharsText.text()).toEqual(remainingChars.toString());
  });
  */
});
