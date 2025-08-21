/* SOHT2 © Licensed under MIT 2025. */
// Inspired by https://github.com/sergeyleschev/react-custom-hooks
import { type DependencyList, useEffect } from 'react';
import useTimeout from './useTimeout';

export default function useDebounce(
  callback: () => void,
  delay: number,
  deps: DependencyList
): () => void {
  const [set, clear] = useTimeout(() => callback(), delay);
  useEffect(clear, [clear]);
  useEffect(() => {
    set();
    return clear;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, clear, set]);
  return clear;
}
