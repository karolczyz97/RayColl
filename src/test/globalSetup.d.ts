declare global {
  var __expoRouterMock: {
    pathname: string;
    router: Record<string, jest.Mock>;
  };
}

export {};
