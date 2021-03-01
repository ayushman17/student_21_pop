import { Lao } from 'model/objects';
import { dispatch, getStore } from '../Storage';
import { addLao, connectToLao, makeCurrentLao } from '../reducers';

const currentLao = makeCurrentLao();

export namespace OpenedLaoStore {

  export function store(lao: Lao): void {
    const laoState = lao.toState();
    dispatch(addLao(laoState));
    dispatch(connectToLao(lao.id));
  }

  // Consider using an alternative way to access the store wherever possible
  export function get(): Lao {
    const lao = currentLao(getStore().getState());

    if (!lao) {
      throw new Error('Error encountered while accessing storage : no currently opened LAO');
    }

    return lao;
  }
}
