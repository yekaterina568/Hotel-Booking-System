const bookingStates = ['CONFIRMED', 'CHECKEDIN', 'CHECKEDOUT', 'CANCELLED', 'ARCHIVED'];
const roomCleaningStates = ['CLEANED', 'CLEANING', 'DIRTY'];
const reportTypes = {
  BOOKINGS: { statuses: bookingStates, dates: 'Booking creation date' },
  ROOMS: { statuses: roomCleaningStates },
  HOTELS: { statuses: bookingStates, dates: 'Booking and review creation dates' },
  REFUNDS: { statuses: ['PENDING', 'COMPLETED', 'FAILED'], dates: 'Refund processing date' },
  REVIEWS: { statuses: ['PENDING', 'PUBLISHED', 'HIDDEN'], dates: 'Review creation date' }
};
const chartColors = ['#3644a5', '#24868b', '#5176b5', '#b65a3b', '#795c9b'];
const countFormat = new Intl.NumberFormat('en');
let dashboardRequest = 0;
let reportRequest = 0;
let chartInstances = [];
let dashboardData = null;
let reportSelection = null;
let reportPage = 1;

function formQuery(form) {
  return new URLSearchParams([...new FormData(form)].filter(([, value]) => value !== ''));
}

function initInsights() {
  const overviewForm = element('dashboardFilters');
  element('dashboardPeriod').addEventListener('change', () => {
    const custom = element('dashboardPeriod').value === 'CUSTOM';
    overviewForm.querySelectorAll('[data-custom-date]').forEach(field => {
      field.hidden = !custom;
      const input = field.querySelector('input');
      input.disabled = !custom;
      input.required = custom;
    });
  });
  overviewForm.addEventListener('submit', event => {
    event.preventDefault();
    loadDashboard();
  });
  element('chartLibrary').addEventListener('load', () => {
    if (dashboardData) renderCharts(dashboardData);
  });

  const reportForm = element('reportFilters');
  element('reportType').addEventListener('change', configureReportFilters);
  reportForm.addEventListener('change', invalidateReport);
  reportForm.addEventListener('submit', event => {
    event.preventDefault();
    loadReport(formQuery(reportForm), 1);
  });
  element('previousReportPage').addEventListener('click', () => loadReport(reportSelection, reportPage - 1));
  element('nextReportPage').addEventListener('click', () => loadReport(reportSelection, reportPage + 1));
  element('downloadReport').addEventListener('click', () => exportReport(false));
  element('printReport').addEventListener('click', () => exportReport(true));
  configureReportFilters();
}

function updateInsightsHotelOptions() {
  ['dashboardHotel', 'reportHotel'].forEach(id => {
    const select = element(id);
    setHotelOptions(select, 'All hotels', select.value);
  });
}

function resetInsights() {
  dashboardRequest++;
  dashboardData = null;
  destroyCharts();
  element('dashboardCharts').replaceChildren();
  element('inventoryMetrics').replaceChildren();
  element('activityMetrics').replaceChildren();
  element('overviewData').hidden = true;
  element('dashboardFilters').reset();
  element('dashboardPeriod').dispatchEvent(new Event('change'));
  element('reportFilters').reset();
  configureReportFilters();
  invalidateReport();
  element('reportRows').replaceChildren();
  element('reportMetadata').replaceChildren();
  element('reportSummary').replaceChildren();
}

async function loadDashboard() {
  const loadId = ++dashboardRequest;
  const isCurrent = () => loadId === dashboardRequest && Boolean(credentials);
  const status = element('overviewFeedback');
  element('overviewData').hidden = true;
  dashboardData = null;
  destroyCharts();
  showFeedback(status, 'Loading dashboard…');
  try {
    const data = await request('/dashboard?' + formQuery(element('dashboardFilters')));
    if (!isCurrent()) return;
    dashboardData = data;
    renderDashboard(data);
    showFeedback(status);
  } catch (error) {
    if (isCurrent()) showFeedback(status, error.message, 'error');
  }
}

function metric(container, label, value, metricId) {
  const card = document.createElement('div');
  card.className = 'panel metric';
  card.dataset.metric = metricId;
  const title = document.createElement('span');
  title.textContent = label;
  const amount = document.createElement('strong');
  amount.textContent = typeof value === 'number' ? countFormat.format(value) : value;
  card.append(title, amount);
  container.append(card);
}

function renderDashboard(data) {
  const inventory = element('inventoryMetrics');
  inventory.replaceChildren();
  Object.entries(data.inventory).forEach(([name, value]) => metric(inventory, 'Total ' + name, value, name));

  const activity = element('activityMetrics');
  const labels = {
    bookings: 'Total bookings', confirmed: 'Confirmed bookings', checkedIn: 'Checked-in bookings',
    checkedOut: 'Checked-out bookings', cancelled: 'Cancelled bookings', archived: 'Archived bookings',
    refunds: 'Total refunds', averagePublishedRating: 'Average published rating'
  };
  activity.replaceChildren();
  Object.entries(labels).forEach(([key, label]) => {
    let value = data.activity[key];
    if (key === 'averagePublishedRating') value = value == null ? '—' : Number(value).toFixed(2) + ' / 5';
    metric(activity, label, value, key);
  });
  const dates = data.filters;
  element('activityDates').textContent = (dates.from ? dates.from + ' through ' + dates.to : 'All time')
    + ' (' + data.zone + ')';
  const undated = data.activity.undatedRefunds;
  element('refundDateNote').hidden = undated === 0;
  element('refundDateNote').textContent = countFormat.format(undated)
    + ' refunds have no processed date and appear only in the all-time total.';
  element('overviewData').hidden = false;
  renderCharts(data);
}

function destroyCharts() {
  chartInstances.forEach(chart => chart.destroy());
  chartInstances = [];
}

function renderCharts(data) {
  destroyCharts();
  const container = element('dashboardCharts');
  container.replaceChildren();
  const trend = points => points.map(point => [
    data.granularity === 'year' ? point.date.slice(0, 4)
      : data.granularity === 'month' ? point.date.slice(0, 7) : point.date,
    point.count
  ]);
  const categories = groups => groups.map(group => [displayStatus(group.label), group.count]);
  const hotelsByName = new Map();
  data.bookingsPerHotel.forEach(hotel => hotelsByName.set(hotel.name, (hotelsByName.get(hotel.name) || 0) + 1));
  const hotelsData = data.bookingsPerHotel.map(hotel => [
    hotelsByName.get(hotel.name) > 1 ? hotel.name + ' (#' + hotel.hotelId + ')' : hotel.name, hotel.count
  ]);
  drawChart(container, 'Bookings over time', 'Period', 'line', trend(data.bookingsOverTime));
  drawChart(container, 'Booking status distribution', 'Status', 'doughnut', categories(data.bookingStatuses));
  drawChart(container, 'Room cleaning status', 'Status', 'bar', categories(data.roomStatuses));
  drawChart(container, 'Bookings per hotel', 'Hotel', 'bar', hotelsData, true);
  drawChart(container, 'Published review ratings', 'Rating', 'bar', categories(data.reviewRatings));
  drawChart(container, 'Refunds over time', 'Period', 'line', trend(data.refundsOverTime));
}

function drawChart(container, title, label, type, rows, horizontal = false) {
  const card = document.createElement('article');
  card.className = 'panel chart-card';
  const heading = document.createElement('h3');
  heading.textContent = title;
  card.append(heading);
  container.append(card);
  if (!rows.some(([, count]) => count > 0)) {
    const empty = document.createElement('p');
    empty.className = 'chart-empty';
    empty.textContent = 'No data for this selection.';
    card.append(empty);
    return;
  }

  if (window.Chart) {
    const chartRows = horizontal ? rows.slice(0, 12) : rows;
    if (chartRows.length < rows.length) {
      const note = document.createElement('p');
      note.className = 'insights-note';
      note.textContent = 'Top 12 hotels by booking count. View chart data for the full list.';
      card.append(note);
    }
    const frame = document.createElement('div');
    frame.className = 'chart-frame';
    if (horizontal) frame.style.height = Math.max(220, chartRows.length * 28) + 'px';
    const canvas = document.createElement('canvas');
    canvas.setAttribute('aria-hidden', 'true');
    frame.append(canvas);
    card.append(frame);
    const countAxis = { beginAtZero: true, ticks: { precision: 0 } };
    chartInstances.push(new Chart(canvas, {
      type,
      data: {
        labels: chartRows.map(([name]) => name),
        datasets: [{
          label: 'Count',
          data: chartRows.map(([, count]) => count),
          borderColor: type === 'doughnut' ? '#fff' : chartColors[0],
          backgroundColor: type === 'doughnut' ? chartColors : (type === 'line' ? '#3644a51a' : chartColors[2]),
          fill: type === 'line',
          pointRadius: rows.length > 35 ? 1 : 3
        }]
      },
      options: {
        indexAxis: horizontal ? 'y' : 'x',
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        plugins: { legend: { display: type === 'doughnut', position: 'bottom' } },
        scales: type === 'doughnut' ? undefined : (horizontal
          ? { x: countAxis, y: { ticks: { autoSkip: false } } }
          : { y: countAxis, x: { ticks: { maxTicksLimit: 8, maxRotation: 0 } } })
      }
    }));
  }

  const details = document.createElement('details');
  details.open = !window.Chart;
  const summary = document.createElement('summary');
  summary.textContent = 'View chart data';
  const table = document.createElement('table');
  table.className = 'chart-values';
  table.setAttribute('aria-label', title + ' data');
  setColumnHeadings(table.createTHead().insertRow(), [label, 'Count']);
  const body = table.createTBody();
  rows.forEach(([name, count]) => {
    const row = addRow(body);
    addCell(row, name);
    addCell(row, countFormat.format(count));
  });
  details.append(summary, table);
  card.append(details);
}

function setColumnHeadings(row, columns) {
  row.replaceChildren(...columns.map(label => {
    const header = document.createElement('th');
    header.scope = 'col';
    header.textContent = label;
    return header;
  }));
}

function configureReportFilters() {
  const type = element('reportType').value;
  const config = reportTypes[type];
  element('reportStatusLabel').textContent = type === 'HOTELS' ? 'Booking status' : 'Status';
  element('reportStatus').replaceChildren(new Option('All statuses', ''),
    ...config.statuses.map(status => new Option(displayStatus(status), status)));
  element('reportFilters').querySelectorAll('[data-report-date]').forEach(field => {
    field.hidden = !config.dates;
    field.querySelector('input').disabled = !config.dates;
  });
  element('reportDateNote').textContent = config.dates
    ? config.dates + '. Start and end dates are inclusive. Leave dates blank for all time.'
    : 'Room inventory shows the current state.';
}

function invalidateReport() {
  reportRequest++;
  reportSelection = null;
  element('reportPreview').hidden = true;
  showFeedback(element('reportFeedback'));
}

async function loadReport(selection, page) {
  if (!selection) return;
  const loadId = ++reportRequest;
  const isCurrent = () => loadId === reportRequest && Boolean(credentials);
  const status = element('reportFeedback');
  element('reportPreview').hidden = true;
  showFeedback(status, 'Generating preview…');
  try {
    const query = new URLSearchParams(selection);
    query.set('page', page);
    const report = await request('/reports?' + query);
    if (!isCurrent()) return;
    reportSelection = selection;
    reportPage = page;
    renderReport(report);
    showFeedback(status);
    if (page !== 1) element('reportPreview').querySelector('.table-container').focus();
  } catch (error) {
    if (isCurrent()) showFeedback(status, error.message, 'error');
  }
}

function renderFields(target, fields) {
  target.replaceChildren(...fields.map(({ name, value }) => {
    const field = document.createElement('div');
    const label = document.createElement('dt');
    label.textContent = name;
    const detail = document.createElement('dd');
    detail.textContent = displayValue(value);
    field.append(label, detail);
    return field;
  }));
}

function renderReport(report) {
  element('reportTitle').textContent = report.title;
  element('reportGenerated').textContent = 'Generated ' + displayDateTime(report.generatedAt);
  renderFields(element('reportMetadata'), report.filters);
  renderFields(element('reportSummary'), report.summary);
  setColumnHeadings(element('reportColumns'), report.columns);
  const rows = element('reportRows');
  rows.replaceChildren();
  report.rows.forEach(values => {
    const row = addRow(rows);
    values.forEach((value, index) => {
      const display = typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:/.test(value)
        ? displayDateTime(value) : displayValue(value);
      addCell(row, display, { label: report.columns[index] });
    });
  });
  if (!report.rows.length) showEmpty(rows, report.columns.length, 'No records match the selected filters.');

  const pages = Math.max(1, Math.ceil(report.total / report.pageSize));
  const start = (report.page - 1) * report.pageSize + 1;
  element('reportResults').textContent = report.rows.length
    ? 'Showing ' + start + '–' + (start + report.rows.length - 1) + ' of ' + countFormat.format(report.total)
    : countFormat.format(report.total) + ' matching records';
  element('reportPageNumber').textContent = 'Page ' + report.page + ' of ' + pages;
  element('reportPages').hidden = pages === 1;
  element('previousReportPage').disabled = report.page <= 1;
  element('nextReportPage').disabled = report.page >= pages;
  element('reportPreview').hidden = false;
}

async function exportReport(printable) {
  const selection = reportSelection;
  if (!selection) return;
  const status = element('reportFeedback');
  const button = element(printable ? 'printReport' : 'downloadReport');
  const popup = printable ? window.open('', '_blank') : null;
  if (printable && !popup) {
    showFeedback(status, 'Allow pop-ups to open the printable report.', 'error');
    return;
  }
  if (popup) {
    popup.opener = null;
    popup.document.body.textContent = 'Loading printable report…';
  }

  button.disabled = true;
  showFeedback(status, printable ? 'Preparing printable report…' : 'Preparing CSV…');
  try {
    const path = printable ? '/reports/print' : '/reports/export';
    const response = await request(path + '?' + selection, { raw: true });
    const blob = await response.blob();
    if (!credentials || selection !== reportSelection) {
      popup?.close();
      return;
    }
    const url = URL.createObjectURL(blob);
    if (popup) {
      popup.location.replace(url);
    } else {
      const link = document.createElement('a');
      link.href = url;
      link.download = response.headers.get('Content-Disposition')?.match(/filename="([^"]+)"/)?.[1] || 'report.csv';
      document.body.append(link);
      link.click();
      link.remove();
    }
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
    showFeedback(status);
  } catch (error) {
    popup?.close();
    if (credentials && selection === reportSelection) showFeedback(status, error.message, 'error');
  } finally {
    button.disabled = false;
  }
}
