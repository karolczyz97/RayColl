export function getReviewAttemptKey(sessionAttempt: number, cardId: string): string {
  return `${sessionAttempt}:${cardId}`;
}

export function startReviewAttempt(
  reviewedAttemptKeys: Set<string>,
  currentAttempt: number,
): number {
  reviewedAttemptKeys.clear();
  return currentAttempt + 1;
}

export function hasCardBeenReviewed(
  reviewedAttemptKeys: Set<string>,
  sessionAttempt: number,
  cardId: string,
): boolean {
  return reviewedAttemptKeys.has(getReviewAttemptKey(sessionAttempt, cardId));
}

export function tryMarkCardReviewed(
  reviewedAttemptKeys: Set<string>,
  sessionAttempt: number,
  cardId: string,
): boolean {
  const reviewAttemptKey = getReviewAttemptKey(sessionAttempt, cardId);
  if (reviewedAttemptKeys.has(reviewAttemptKey)) {
    return false;
  }

  reviewedAttemptKeys.add(reviewAttemptKey);
  return true;
}
