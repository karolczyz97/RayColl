export const CARD_ORDERS = {
  sequential: 'sequential',
  random: 'random',
  hardest: 'hardest',
} as const;

export type CardOrder = (typeof CARD_ORDERS)[keyof typeof CARD_ORDERS];

export const DEFAULT_CARD_ORDER: CardOrder = CARD_ORDERS.sequential;

export const CARD_ORDER_OPTIONS: { value: CardOrder; labelKey: string }[] = [
  { value: CARD_ORDERS.sequential, labelKey: 'settings.card_order.sequential' },
  { value: CARD_ORDERS.random, labelKey: 'settings.card_order.random' },
  { value: CARD_ORDERS.hardest, labelKey: 'settings.card_order.hardest' },
];

const VALID_CARD_ORDERS = new Set<CardOrder>(Object.values(CARD_ORDERS));

export function normalizeCardOrder(value: unknown): CardOrder {
  return VALID_CARD_ORDERS.has(value as CardOrder) ? (value as CardOrder) : DEFAULT_CARD_ORDER;
}
