describe('Neobank Dark Mode Toggle & Persistence', () => {
  it('toggles dark class on html and persists selection across reloads', () => {
    cy.visit('/', {
      onBeforeLoad(win) {
        win.localStorage.setItem(
          'zheton_state_v2',
          JSON.stringify({
            accounts: [],
            transactions: [],
            holdings: [],
            commitments: [],
            budgetCaps: [],
            cycleConfig: { anchorDay: 1, targetMonthlyBudgetToman: 0 },
            privacyMode: false,
            hasSeenOnboarding: true,
            themeMode: 'light',
          }),
        );
      },
    });

    cy.get('html').should('not.have.class', 'dark');
    cy.get('[data-testid="theme-toggle-button"]').click();
    cy.get('html').should('have.class', 'dark');

    cy.reload();
    cy.get('html').should('have.class', 'dark');

    cy.get('[data-testid="theme-toggle-button"]').click();
    cy.get('html').should('not.have.class', 'dark');
  });
});
