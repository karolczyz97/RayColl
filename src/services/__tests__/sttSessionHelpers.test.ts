import { createSttSession } from '@/services/sttSessionHelpers';

describe('createSttSession', () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('is not resolved initially', () => {
    const session = createSttSession(5000, 2000, jest.fn(), jest.fn());
    expect(session.resolved()).toBe(false);
  });

  it('marks as resolved after setResolved', () => {
    const session = createSttSession(5000, 2000, jest.fn(), jest.fn());
    session.setResolved();
    expect(session.resolved()).toBe(true);
  });

  it('fires onHardTimeout after the hard timeout', () => {
    const onHardTimeout = jest.fn();
    createSttSession(5000, 2000, onHardTimeout, jest.fn());

    expect(onHardTimeout).not.toHaveBeenCalled();
    jest.advanceTimersByTime(5000);
    expect(onHardTimeout).toHaveBeenCalledTimes(1);
  });

  it('does not fire onHardTimeout if already resolved', () => {
    const onHardTimeout = jest.fn();
    const session = createSttSession(5000, 2000, onHardTimeout, jest.fn());

    session.setResolved();
    jest.advanceTimersByTime(5000);
    expect(onHardTimeout).not.toHaveBeenCalled();
  });

  it('fires onInactivityTimeout after inactivity period once reset', () => {
    const onInactivity = jest.fn();
    const session = createSttSession(5000, 2000, jest.fn(), onInactivity);

    session.resetInactivity();
    expect(onInactivity).not.toHaveBeenCalled();
    jest.advanceTimersByTime(2000);
    expect(onInactivity).toHaveBeenCalledTimes(1);
  });

  it('cancelTimers prevents both timeouts from firing', () => {
    const onHardTimeout = jest.fn();
    const onInactivity = jest.fn();
    const session = createSttSession(5000, 2000, onHardTimeout, onInactivity);

    session.cancelTimers();
    jest.advanceTimersByTime(10000);
    expect(onHardTimeout).not.toHaveBeenCalled();
    expect(onInactivity).not.toHaveBeenCalled();
  });

  it('resetInactivity resets the inactivity timer', () => {
    const onInactivity = jest.fn();
    const session = createSttSession(5000, 2000, jest.fn(), onInactivity);

    jest.advanceTimersByTime(1500);
    session.resetInactivity();
    jest.advanceTimersByTime(1500);
    expect(onInactivity).not.toHaveBeenCalled();
    jest.advanceTimersByTime(500);
    expect(onInactivity).toHaveBeenCalledTimes(1);
  });
});
