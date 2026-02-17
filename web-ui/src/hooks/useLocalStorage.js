import { useState, useEffect } from 'react';

/**
 * Custom React hook for localStorage persistence with silent fallback
 * Handles corrupted/missing data gracefully, logs warnings but never throws
 *
 * @param {string} key - localStorage key
 * @param {*} defaultValue - Default value when localStorage is empty or fails
 * @returns {[*, function, function]} - [value, setValue, reset] tuple
 */
export default function useLocalStorage(key, defaultValue) {
  // Lazy initialization: try to restore from localStorage, fallback to defaultValue
  const [value, setValue] = useState(() => {
    try {
      const item = localStorage.getItem(key);
      if (item !== null) {
        return JSON.parse(item);
      }
      return defaultValue;
    } catch (error) {
      console.warn(`[useLocalStorage] Failed to read key "${key}" from localStorage:`, error);
      return defaultValue;
    }
  });

  // Persist value changes to localStorage
  useEffect(() => {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch (error) {
      // Silently handle QuotaExceededError and other localStorage failures
      console.warn(`[useLocalStorage] Failed to write key "${key}" to localStorage:`, error);
    }
  }, [key, value]);

  // Reset function to restore default value
  const reset = () => {
    setValue(defaultValue);
  };

  return [value, setValue, reset];
}
