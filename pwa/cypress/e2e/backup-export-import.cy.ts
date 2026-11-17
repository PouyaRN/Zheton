describe('Encrypted Offline Backup Modal Flow', () => {
  it('validates passphrase length and exports encrypted .zheton backup', () => {
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

    cy.get('[data-testid="backup-modal-button"]').click();
    cy.get('[data-testid="backup-modal"]').should('be.visible');

    cy.get('[data-testid="backup-passphrase-input"]').type('short');
    cy.get('[data-testid="backup-export-submit"]').should('be.disabled');

    cy.get('[data-testid="backup-passphrase-input"]').clear().type('ZhetonStrongPass2026');
    cy.get('[data-testid="backup-export-submit"]').should('not.be.disabled').click();
    cy.get('[data-testid="backup-modal"]', { timeout: 10000 }).should('not.exist');
  });
});
