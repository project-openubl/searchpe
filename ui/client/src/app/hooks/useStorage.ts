import type React from "react";
import { useCallback, useEffect, useState } from "react";

type StorageType = "localStorage" | "sessionStorage";

interface IGetValueFromStorageParams<T> {
  storageType: StorageType;
  key: string;
  defaultValue: T;
}

const getValueFromStorage = <T>({
  storageType,
  key,
  defaultValue,
}: IGetValueFromStorageParams<T>): T => {
  if (typeof window === "undefined") return defaultValue;
  try {
    const itemJSON = window[storageType].getItem(key);
    return itemJSON ? (JSON.parse(itemJSON) as T) : defaultValue;
  } catch (error) {
    console.error(error);
    return defaultValue;
  }
};

interface ISetValueInStorageParams<T> {
  storageType: StorageType;
  key: string;
  newValue: T | undefined;
}

const setValueInStorage = <T>({
  storageType,
  key,
  newValue,
}: ISetValueInStorageParams<T>) => {
  if (typeof window === "undefined") return;
  try {
    if (newValue !== undefined) {
      const newValueJSON = JSON.stringify(newValue);
      window[storageType].setItem(key, newValueJSON);
      if (storageType === "localStorage") {
        // setItem only causes the StorageEvent to be dispatched in other windows. We dispatch it here
        // manually so that all instances of useLocalStorage on this window also react to this change.
        window.dispatchEvent(
          new StorageEvent("storage", { key, newValue: newValueJSON }),
        );
      }
    } else {
      window[storageType].removeItem(key);
      if (storageType === "localStorage") {
        window.dispatchEvent(
          new StorageEvent("storage", { key, newValue: null }),
        );
      }
    }
  } catch (error) {
    console.error(error);
  }
};

interface IUseStorageOptions<T> {
  isEnabled?: boolean;
  type: StorageType;
  key: string;
  defaultValue: T;
}

const useStorage = <T>({
  isEnabled = true,
  type,
  key,
  defaultValue,
}: IUseStorageOptions<T>): [T, React.Dispatch<React.SetStateAction<T>>] => {
  const [cachedValue, setCachedValue] = useState<T>(() =>
    getValueFromStorage({ storageType: type, key, defaultValue }),
  );

  const usingStorageEvents =
    type === "localStorage" && typeof window !== "undefined" && isEnabled;

  const setValue: React.Dispatch<React.SetStateAction<T>> = useCallback(
    (newValueOrFn: T | ((prevState: T) => T)) => {
      const newValue =
        newValueOrFn instanceof Function
          ? newValueOrFn(
              getValueFromStorage({ storageType: type, key, defaultValue }),
            )
          : newValueOrFn;
      setValueInStorage({ storageType: type, key, newValue });
      if (!usingStorageEvents) {
        setCachedValue(newValue);
      }
    },
    [type, key, defaultValue, usingStorageEvents],
  );

  useEffect(() => {
    if (!usingStorageEvents) return;
    const onStorageUpdated = (event: StorageEvent) => {
      if (event.key === key) {
        setCachedValue(
          event.newValue ? JSON.parse(event.newValue) : defaultValue,
        );
      }
    };
    window.addEventListener("storage", onStorageUpdated);
    return () => {
      window.removeEventListener("storage", onStorageUpdated);
    };
  }, [key, defaultValue, usingStorageEvents]);

  return [cachedValue, setValue];
};

export type UseStorageTypeOptions<T> = Omit<IUseStorageOptions<T>, "type">;

export const useLocalStorage = <T>(
  options: UseStorageTypeOptions<T>,
): [T, React.Dispatch<React.SetStateAction<T>>] =>
  useStorage({ ...options, type: "localStorage" });
