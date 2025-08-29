/* SOHT2 © Licensed under MIT 2025. */
// Inspired by https://github.com/sergeyleschev/react-custom-hooks
import { useEffect } from 'react';
import useTimeout from './useTimeout';

export default function useDebounce(delay: number, callback: () => void): () => void {
  const [set, clear] = useTimeout(delay, () => callback());
  useEffect(clear, [clear]);
  useEffect(() => {
    set();
    return clear;
  }, [clear, set]);
  return clear;
}
