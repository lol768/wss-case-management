/* eslint-env browser */
import * as d3 from 'd3';
import log from 'loglevel';

export default function D3Chart(container) {
  if (container) {
    const margin = {
      top: 40,
      right: 240,
      bottom: 70,
      left: 30,
    };
    const width = container.offsetWidth - margin.left - margin.right;
    const height = 550 - margin.top - margin.bottom;

    const svg = d3.select(container).append('svg')
      .attr('width', width + margin.left + margin.right)
      .attr('height', height + margin.top + margin.bottom)
      .append('g')
      .attr('transform', `translate(${margin.left}, ${margin.top})`);

    const x = d3.scaleBand().rangeRound([0, width], 0.08);
    const y = d3.scaleLinear().range([height, 0]);
    const colours = d3.scaleOrdinal(d3.schemeSet2);

    let working = false;

    document.addEventListener('click', (event) => {
      const { target } = event;
      const trigger = target.closest('.d3-chartable');

      if (working) {
        return;
      }
      working = true;

      if (target.classList.contains('d3-hider')) {
        container.classList.add('collapsed');
        svg.selectAll('*').remove();
        working = false;
      } else if (trigger) {
        const { src, title } = trigger.dataset;

        const spinner = document.createElement('i');
        spinner.classList.add('far', 'fa-fw', 'fa-spin', 'fa-spinner');
        trigger.parentNode.insertBefore(spinner, trigger.nextSibling);

        if (src) {
          d3.json(src)
            .then((json) => {
              spinner.parentNode.removeChild(spinner);

              if (!json.success || !json.data) {
                throw new Error('Unsuccessful JSON response');
              } else {
                // reset
                svg.selectAll('*').remove();

                // get date range
                const dateRange = Object.values(json.data[0])[0].map(d => d.day);

                // parse data
                const transposedData = json.data.reduce((acc, teamSet) => {
                  const teamName = Object.keys(teamSet)[0];
                  teamSet[teamName].forEach((kv) => {
                    const i = acc.findIndex(el => el.date === kv.day);
                    if (i > -1) acc[i][teamName] = kv.value;
                  });
                  return acc;
                }, dateRange.map(date => ({ date })));

                const metadata = json.data.map((teamSet) => {
                  const name = Object.keys(teamSet)[0];
                  return {
                    name,
                    className: name.toLowerCase().replace(/[^a-z]/, '-'),
                  };
                });

                // eslint-disable-next-line max-len
                const maxValue = transposedData.reduce((acc, datum) => {
                  const values = Object.values(datum);
                  values.shift();
                  return Math.max(acc, values.reduce((sum, d) => sum + d));
                }, 0);

                // scale the range of the data
                x.domain(dateRange);
                y.domain([0, maxValue]);

                // Add the X Axis
                svg.append('g')
                  .attr('transform', `translate(0, ${height})`)
                  .call(d3.axisBottom(x))
                  .selectAll('text')
                  .style('text-anchor', 'end')
                  .attr('dx', '-.8em')
                  .attr('dy', '.15em')
                  .attr('transform', 'rotate(-65)');

                // Add the Y Axis
                svg.append('g')
                  .call(d3.axisLeft(y));

                // stack data
                const dataset = d3.stack()
                  .keys(metadata.map(m => m.name))(transposedData);

                // Create groups for each series, rects for each segment
                const groups = svg.selectAll('g.bars')
                  .data(dataset)
                  .enter()
                  .append('g')
                  .attr('class', (d, i) => metadata[i].className)
                  .style('fill', (d, i) => colours(metadata[i].className));

                groups.selectAll('rect')
                  .data(d => d)
                  .enter()
                  .append('rect')
                  .attr('x', d => x(d.data.date) + 1)
                  .attr('y', d => y(d[1]))
                  .attr('height', d => y(d[0]) - y(d[1]))
                  .attr('width', x.bandwidth() - 2)
                  /* eslint-disable no-use-before-define */
                  .on('mouseover', () => tooltip.style('display', null))
                  .on('mouseout', () => tooltip.style('display', 'none'))
                  .on('mousemove', (d, i, nodes) => {
                    const xPosition = d3.mouse(nodes[i])[0] - 15;
                    const yPosition = d3.mouse(nodes[i])[1] - 25;
                    tooltip.attr('transform', `translate(${xPosition},${yPosition})`);
                    tooltip.select('text').text(d[1] - d[0]);
                  });
                /* eslint-enable no-use-before-define */

                const tooltip = svg.append('g')
                  .attr('class', 'svg-tooltip')
                  .style('display', 'none');

                tooltip.append('rect')
                  .attr('width', 30)
                  .attr('height', 20)
                  .attr('stroke', '#333')
                  .attr('fill', 'white')
                  .style('opacity', 0.85);

                tooltip.append('text')
                  .attr('x', 15)
                  .attr('dy', '1.2em')
                  .style('text-anchor', 'middle')
                  .attr('font-size', '12px')
                  .attr('font-weight', 'bold');

                // Draw legend
                const legend = svg.selectAll('.legend')
                  .data(metadata)
                  .enter().append('g')
                  .attr('class', 'legend')
                  .attr('transform', (d, i) => (`translate(30,${i * 19})`));

                legend.append('rect')
                  .attr('x', width - 18)
                  .attr('width', 18)
                  .attr('height', 18)
                  .style('fill', (d, i) => colours(metadata[i].className));

                legend.append('text')
                  .attr('x', width + 5)
                  .attr('y', 9)
                  .attr('dy', '.35em')
                  .style('text-anchor', 'start')
                  .text((d, i) => metadata[i].name);

                svg.append('text')
                  .attr('x', width / 2)
                  .attr('y', 0 - margin.top / 2)
                  .attr('text-anchor', 'middle')
                  .text(title);

                container.classList.remove('collapsed');
                working = false;
              }
            })
            .catch((err) => {
              log.error(err);
              const alert = document.createElement('p');
              alert.classList.add('alert', 'alert-danger');
              alert.innerHTML = 'Sorry, chart could not be created.';
              container.removeChild(svg);
              container.appendChild(alert);
              container.classList.remove('collapsed');
              working = false;
            });
        }
      } else {
        working = false;
      }
    }, false);
  }
}
