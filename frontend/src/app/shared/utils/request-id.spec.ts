import { newRequestId } from './request-id';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe('newRequestId', () => {
  it('genera UUID v4 distintos', () => {
    const a = newRequestId();
    const b = newRequestId();
    expect(a).toMatch(UUID);
    expect(a).not.toBe(b);
  });
});
