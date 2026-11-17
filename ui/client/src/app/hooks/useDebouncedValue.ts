import { useEffect, useState } from "react";

// Copy of `value` that updates only after it stops changing for `delayMs`, so
// filter typing doesn't fire a server request per keystroke.
export function useDebouncedValue<T>({
  value,
  delayMs = 300,
}: {
  value: T;
  delayMs?: number;
}): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);

  return debounced;
}
