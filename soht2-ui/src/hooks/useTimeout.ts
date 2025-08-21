/* SOHT2 © Licensed under MIT 2025. */
import { useCallback, useRef } from 'react';

export default function useTimeout(
  callback: () => void,
  delay: number
): Readonly<[() => void, () => void]> {
  const timeoutRef = useRef<number>(undefined);

  const cancel = useCallback(() => {
    if (timeoutRef.current) {
      window.clearTimeout(timeoutRef.current);
      timeoutRef.current = undefined;
    }
  }, []);

  const cb = useCallback(() => {
    cancel();
    callback();
  }, [callback, cancel]);

  const set = useCallback(() => {
    cancel();
    timeoutRef.current = window.setTimeout(cb, delay);
  }, [cb, cancel, delay]);

  return [set, cancel];
}
