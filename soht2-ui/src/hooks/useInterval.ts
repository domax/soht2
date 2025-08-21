/* SOHT2 © Licensed under MIT 2025. */
import { useCallback, useRef } from 'react';

export default function useInterval(
  callback: () => void,
  delay: number
): Readonly<[() => void, () => void]> {
  const intervalRef = useRef<number>(undefined);

  const cancel = useCallback(() => {
    if (intervalRef.current) {
      window.clearInterval(intervalRef.current);
      intervalRef.current = undefined;
    }
  }, []);

  const set = useCallback(() => {
    cancel();
    intervalRef.current = window.setInterval(callback, delay);
  }, [callback, cancel, delay]);

  return [set, cancel];
}
