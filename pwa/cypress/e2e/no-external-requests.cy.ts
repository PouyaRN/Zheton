describe('Zero External Font or Analytics Requests', () => {
  it('makes zero requests to fonts.googleapis.com, fonts.gstatic.com, or third-party trackers', () => {
    const externalRequests: string[] = [];
    cy.intercept({ url: /^https?:\/\/(?!127\.0\.0\.1|localhost).*/ }, (req) => {
      externalRequests.push(req.url);
      req.reply({ statusCode: 204, body: '' });
    });

    cy.visit('/');
    cy.get('svg[data-icon]').should('have.length.greaterThan', 5);
    cy.then(() => {
      const forbidden = externalRequests.filter((url) =>
        /fonts\.googleapis\.com|fonts\.gstatic\.com|google-analytics|googletagmanager/i.test(url),
      );
      expect(forbidden).to.deep.equal([]);
    });
  });
});

