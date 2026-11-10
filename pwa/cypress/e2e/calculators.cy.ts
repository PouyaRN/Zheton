describe('Calculators Modal Flow', () => {
  it('opens the smart calculators modal and renders SVG icons', () => {
    cy.visit('/', {
      onBeforeLoad(win) {
        win.localStorage.setItem(
          'zheton_fintech_state_v2',
          JSON.stringify({
            accounts: [],
            transactions: [],
            holdings: [],
            commitments: [],
            monthlyBudgetToman: 25000000,
            cycleStartDay: 1,
            privacyMode: false,
            onboardingCompleted: true,
          }),
        );
      },
    });

    cy.get('button[aria-label="دستیار هوشمند گرداول"]').click();
    cy.get('svg[data-icon="calculate"]').should('be.visible');
  });
});
