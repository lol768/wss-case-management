/* eslint-env browser */
import * as d3 from 'd3';

export default function D3Chart(container) {
  if (container) {
    const margin = {
      top: 50,
      right: 50,
      bottom: 50,
      left: 50,
    };
    const width = container.parentElement.offsetWidth - margin.left - margin.right;
    const height = 500 - margin.top - margin.bottom;

    const svg = d3.select(container).append('svg')
      .attr('width', width + margin.left + margin.right)
      .attr('height', height + margin.top + margin.bottom)
      .append('g')
      .attr('transform', `translate(${margin.left}, ${margin.top})`);

    const x = d3.scaleTime().range([0, width]);
    const y = d3.scaleLinear().range([height, 0]);
    const colours = d3.scaleOrdinal(d3.schemeCategory10);

    const parseDate = d3.timeParse('%Y-%m-%d');
    // const formatDate = d3.timeFormat('%b %d');

    const drawLine = (title, data, maxValue) => {
      // generate a className
      const className = title.toLowerCase().replace(/[^a-z]/, '-');

      // format the data
      const parsedData = data.map(d => ({
        day: parseDate(d.day),
        value: +d.value,
      }));

      // sort by date
      parsedData.sort((a, b) => (a.day - b.day));

      // scale the range of the data
      x.domain(d3.extent(parsedData, d => (d.day)));
      y.domain([0, maxValue]);

      // Add the line
      svg.append('path')
        .datum(parsedData)
        .attr('class', `line ${className}`)
        .style('stroke', colours(className))
        .attr('d', d3.line()
          .x(d => x(d.day))
          .y(d => y(d.value)));

      // Add the X Axis
      svg.append('g')
        .attr('transform', `translate(0, ${height})`)
        .call(d3.axisBottom(x));

      // Add the Y Axis
      svg.append('g')
        .call(d3.axisLeft(y));
    };

    const getMaxValue = teamSets => teamSets.reduce((acc, teamSet) => {
      const dayPairs = Object.values(teamSet)[0] || [];
      const valueArray = dayPairs.map(m => m.value);
      return Math.max(acc, valueArray.reduce((a, b) => Math.max(a, b)));
    }, 0);

    document.addEventListener('click', (event) => {
      if (event.target.classList.contains('d3-chartable')) {
        const jsonSrc = event.target.dataset.src;

        if (jsonSrc) {
          d3.selectAll('.line').remove();

          d3.json(jsonSrc)
            .then((json) => {
              if (!json.success || !json.data) {
                throw new Error('Valid JSON data not found');
              } else {
                const maxValue = getMaxValue(json.data);

                json.data.forEach((teamSet) => {
                  const title = Object.keys(teamSet)[0];
                  const values = Object.values(teamSet)[0];
                  drawLine(title, values, maxValue);
                });

                container.classList.remove('hidden');
              }
            })
            .catch((err) => {
              throw err;
            });
        }
      }
    }, false);
  }
}
