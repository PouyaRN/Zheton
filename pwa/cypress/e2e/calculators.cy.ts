describe('Calculators Modal Flow', () => {
  it('opens the smart calculators modal and renders SVG icons', () => {
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
            cycleConfig: {
              anchorDay: 1,
              targetMonthlyBudgetToman: 25000000,
            },
            privacyMode: false,
            hasSeenOnboarding: true,
          }),
        );
      },
    });

    cy.get('button[aria-label="دستیار هوشمند گرداول"]').click();
    cy.get('svg[data-icon="calculate"]').should('be.visible');
  });
});
