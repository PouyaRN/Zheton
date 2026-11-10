describe('Content Security Policy Enforcement', () => {
  it('includes a strict Content-Security-Policy meta tag with font-src self', () => {
    cy.visit('/');
    cy.get('meta[http-equiv="Content-Security-Policy"]')
      .should('exist')
      .and('have.attr', 'content')
      .and('include', "font-src 'self'")
      .and('include', "default-src 'self'");
  });
});

