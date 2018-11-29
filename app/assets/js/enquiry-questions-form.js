import $ from 'jquery';

export default class EnquiryQuestionsForm {
  constructor($form) {
    this.$form = $form;

    // Re-calculate on change
    $form.on('input change', this.onChange.bind(this));

    // Handle init
    $form.on('click', '.init-questions button.btn-primary', this.start.bind(this));
  }

  static isSectionComplete(i, section) { // eslint-disable-line no-unused-vars
    const $section = $(section);
    const $questions = $section.find('.form-group');
    const $questionsWithValue = $questions.filter(EnquiryQuestionsForm.hasValueOrOptional);

    return $questionsWithValue.length === $questions.length && ($questionsWithValue.find('input[type="radio"][name*="."]').length === 0 || $questionsWithValue.find('input[type="radio"][name*="."][value!="false"]:enabled:checked').length > 0);
  }

  static hasValueOrOptional(i, question) { // eslint-disable-line no-unused-vars
    const $question = $(question);

    const $radios = $question.find('input[type="radio"]');
    const $checkedRadios = $radios.filter(':enabled:checked');

    let value;
    let hasValue;
    if ($radios.length) {
      hasValue = $checkedRadios.length > 0;
    } else {
      value = $question.find(':input').val();
      hasValue = value && value.length > 0;
    }

    return hasValue || $question.find(':input[type!="file"]').length === 0;
  }

  onChange({ target = undefined } = {}, initial = false) { // eslint-disable-line no-unused-vars
    if (!initial && !this.started) this.start({}, false);

    const $sections = this.$form.children('fieldset:not(:last-child)');

    let showNextSection = this.started
      || this.$form.find('.init-questions').hasClass('started');
    let animateNextQuestion = false;
    let showSubmitButtons = false;
    $sections.each((i, section) => {
      const $section = $(section);

      if (showNextSection) {
        $section.show();
      } else {
        $section.hide();
      }

      const $questions = $section.find('.form-group');

      let showNextQuestion = showNextSection;
      let skipRestOfSection = false;
      $questions.each((j, question) => {
        const $question = $(question);

        if ($question.find('input[type="file"]').length > 0) {
          if (this.$form.find('input[name="mentalHealthFields.reasonableAdjustments"][value="false"]:checked').length === 1
            && this.$form.find('input[name="mentalHealthFields.studySkills"][value="false"]:checked').length === 1
            && this.$form.find('input[name="mentalHealthFields.generalAdvice"][value="false"]:checked').length === 1) {
            skipRestOfSection = true;
          }
        }

        if (showNextQuestion && !skipRestOfSection) {
          if (animateNextQuestion) {
            $question.slideDown('fast', 'swing');
          } else {
            $question.show();
          }

          $question.find(':input').prop('disabled', false);
        } else {
          $question.hide().find(':input').prop('disabled', true);
          $question.find('input[type="radio"]:checked').each((k, radio) => {
            const $radio = $(radio);
            $radio.prop('checked', false);
            $radio.parent('label.btn').removeClass('btn-primary').removeClass('active').addClass('btn-default');
          });
          $question.find(':input:not([type="radio"])').val('');

          if (skipRestOfSection) return;
        }

        showNextQuestion = showNextQuestion && EnquiryQuestionsForm.hasValueOrOptional(j, question);
        animateNextQuestion = target && showNextQuestion && $question.find(':input').has(target);

        skipRestOfSection = j === 0 && $question.find('input[type="radio"][value="false"]:enabled:checked').length > 0;
      });

      if (EnquiryQuestionsForm.isSectionComplete(i, section)) {
        showNextSection = false;
        showSubmitButtons = true;
      } else {
        showNextSection = showNextQuestion;
      }
    });

    const $buttons = this.$form.children('fieldset:last-child');
    if (showSubmitButtons) {
      $buttons.find(':input').prop('disabled', false);
      $buttons.find('.form-group').show();

      if (!$buttons.is(':visible') && !initial) {
        $buttons.slideDown('fast', 'swing');
      } else {
        $buttons.show();
      }
    } else {
      $buttons.find(':input').prop('disabled', true);
      $buttons.find('.form-group').hide();
      $buttons.hide();
    }
  }

  start({ target = undefined } = {}, animate = true) { // eslint-disable-line no-unused-vars
    this.started = true;

    const $container = this.$form.find('.init-questions');
    if ($container.hasClass('started')) return;

    if (animate) {
      $container.slideUp('fast', 'swing', () => $container.addClass('started'));

      const $section = this.$form.children('fieldset').first();
      $section.show();

      const $firstQuestion = $section.find('.form-group').first();
      $firstQuestion.slideDown('fast', 'swing');
      $firstQuestion.find(':input').prop('disabled', false);
    } else {
      $container.addClass('started');
    }
  }

  static bindTo(form) {
    const $form = $(form);
    if (!$form.data('enquiry-questions-form')) {
      $form.data('enquiry-questions-form', new EnquiryQuestionsForm($form));
    }

    $form.data('enquiry-questions-form').onChange({}, true);
  }
}
