describe('RTL & Accessibility Contracts', () => {
  it('enforces lang="fa" dir="rtl" and decorative aria-hidden on default SVG icons', () => {
    cy.visit('/');
    cy.get('html').should('have.attr', 'lang', 'fa').and('have.attr', 'dir', 'rtl');
    cy.get('svg[data-icon]').first().should('have.attr', 'aria-hidden', 'true');
  });
});

