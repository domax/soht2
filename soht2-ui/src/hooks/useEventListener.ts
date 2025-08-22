/* SOHT2 © Licensed under MIT 2025. */
import { useEffect, useRef } from 'react';

export default function useEventListener<E extends Event>(
  eventType: string,
  callback: (e: E) => void,
  element: EventTarget = window
): void {
  const callbackRef = useRef(callback as EventListener);

  useEffect(() => {
    callbackRef.current = callback as EventListener;
  }, [callback]);

  useEffect(() => {
    element.addEventListener(eventType, callbackRef.current);
    return () => element.removeEventListener(eventType, callbackRef.current);
  }, [eventType, element]);
}
