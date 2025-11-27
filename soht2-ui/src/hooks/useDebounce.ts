/* SOHT2 © Licensed under MIT 2025. */
/* eslint-disable react-hooks/exhaustive-deps */
// Inspired by https://github.com/sergeyleschev/react-custom-hooks
import { type DependencyList, useEffect } from 'react';
import useTimeout from './useTimeout';

export default function useDebounce(
  delay: number,
  callback: () => void,
  deps: DependencyList
): () => void {
  const [set, clear] = useTimeout(delay, callback);
  useEffect(clear, []);
  useEffect(() => {
    set();
    return clear;
  }, [...deps, clear]);
  return clear;
}
