import { clamp } from '@/utils/math';

export function hexToRgba(color: string, alpha: number): string {
  const finalAlpha = clamp(alpha, 0, 1);
  const normalized = color.trim();

  const hex = normalized.startsWith('#') ? normalized.slice(1) : '';
  const expandedHex =
    hex.length === 3
      ? hex
          .split('')
          .map((part) => `${part}${part}`)
          .join('')
      : hex;

  if (/^[0-9a-fA-F]{6}$/.test(expandedHex)) {
    const red = Number.parseInt(expandedHex.slice(0, 2), 16);
    const green = Number.parseInt(expandedHex.slice(2, 4), 16);
    const blue = Number.parseInt(expandedHex.slice(4, 6), 16);
    return `rgba(${red}, ${green}, ${blue}, ${finalAlpha})`;
  }

  const rgbaMatch = normalized.match(
    /^rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})(?:\s*,\s*([0-9.]+))?\s*\)$/i,
  );

  if (rgbaMatch) {
    const [, red, green, blue, sourceAlpha] = rgbaMatch;
    const parsedSourceAlpha = sourceAlpha === undefined ? 1 : Number(sourceAlpha);
    const adjustedAlpha = clamp(parsedSourceAlpha * finalAlpha, 0, 1);
    const r = clamp(Number(red), 0, 255);
    const g = clamp(Number(green), 0, 255);
    const b = clamp(Number(blue), 0, 255);
    return `rgba(${r}, ${g}, ${b}, ${adjustedAlpha})`;
  }

  return `rgba(0, 0, 0, ${finalAlpha})`;
}
