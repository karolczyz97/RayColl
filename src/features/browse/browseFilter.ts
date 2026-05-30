import type { SrsCardCategory } from '../../srs/srsEngine';

/**
 * Pure reducer for category multi-select toggle.
 *
 * nonEmptyCategories lists which categories actually have cards — if the user
 * selects all non-empty categories, the selection resets to [] ("show all")
 * so that empty categories don't prevent the reset.
 */
export function toggleCategoryReducer(
  prev: SrsCardCategory[],
  category: SrsCardCategory,
  nonEmptyCategories: SrsCardCategory[],
): SrsCardCategory[] {
  const next = prev.includes(category)
    ? prev.filter((c) => c !== category)
    : [...prev, category];

  // Reset when every non-empty category is selected
  const allNonEmptySelected =
    nonEmptyCategories.length > 0 &&
    nonEmptyCategories.every((c) => next.includes(c));

  return allNonEmptySelected ? [] : next;
}

/**
 * Given selected categories and a card's category, returns whether
 * the card should be visible. Empty selection = show all.
 */
export function shouldShowCard(
  selectedCategories: SrsCardCategory[],
  cardCategory: SrsCardCategory,
): boolean {
  if (selectedCategories.length === 0) return true;
  return selectedCategories.includes(cardCategory);
}
